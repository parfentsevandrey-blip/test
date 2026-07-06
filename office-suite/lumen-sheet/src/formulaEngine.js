// formulaEngine.js — hand-rolled formula tokenizer, recursive-descent parser
// and evaluator. No third-party formula-parsing package is used.
//
// Public surface:
//   tokenize(input)         -> Token[]
//   parseFormula(input)     -> AST
//   collectRefs(ast)        -> Set<string> of plain (unlocked) cell keys read by the formula
//   evaluate(ast, ctx)      -> number | string | boolean | FormulaError
//   FormulaError, isError
//
// ctx passed to evaluate() must provide:
//   getCellValue(ref: string) -> scalar value or FormulaError
//   getRange(rangeRef: string) -> flat array of scalar values
//   depth (optional, for recursion guarding — not required by callers)

import { isCellRefToken, parseCellRefStr, cellKeyFromRC, iterRangeKeys } from './refUtils.js';

// ---------------------------------------------------------------------------
// Errors
// ---------------------------------------------------------------------------

export class FormulaError {
  constructor(code) {
    this.error = code;
  }
  toString() {
    return this.error;
  }
}

export function isError(v) {
  return v instanceof FormulaError;
}

// ---------------------------------------------------------------------------
// Tokenizer
// ---------------------------------------------------------------------------

const TWO_CHAR_OPS = ['<=', '>=', '<>'];
const ONE_CHAR_OPS = '+-*/^&=<>(),:';

export function tokenize(input) {
  const tokens = [];
  const n = input.length;
  let i = 0;
  while (i < n) {
    const ch = input[i];
    if (ch === ' ' || ch === '\t' || ch === '\n' || ch === '\r') {
      i++;
      continue;
    }
    if (ch === '"') {
      let j = i + 1;
      let str = '';
      while (j < n && input[j] !== '"') {
        if (input[j] === '\\' && j + 1 < n) {
          str += input[j + 1];
          j += 2;
        } else {
          str += input[j];
          j++;
        }
      }
      tokens.push({ type: 'STRING', value: str });
      i = j + 1;
      continue;
    }
    if (/[0-9]/.test(ch) || (ch === '.' && /[0-9]/.test(input[i + 1] || ''))) {
      let j = i;
      let numStr = '';
      let seenDot = false;
      while (j < n && (/[0-9]/.test(input[j]) || (input[j] === '.' && !seenDot))) {
        if (input[j] === '.') seenDot = true;
        numStr += input[j];
        j++;
      }
      // optional exponent, e.g. 1e3
      if (j < n && (input[j] === 'e' || input[j] === 'E')) {
        let k = j + 1;
        let exp = input[j];
        if (k < n && (input[k] === '+' || input[k] === '-')) {
          exp += input[k];
          k++;
        }
        if (k < n && /[0-9]/.test(input[k])) {
          while (k < n && /[0-9]/.test(input[k])) {
            exp += input[k];
            k++;
          }
          numStr += exp;
          j = k;
        }
      }
      tokens.push({ type: 'NUMBER', value: parseFloat(numStr) });
      i = j;
      continue;
    }
    if (/[A-Za-z_$]/.test(ch)) {
      let j = i;
      let idStr = '';
      while (j < n && /[A-Za-z0-9_$]/.test(input[j])) {
        idStr += input[j];
        j++;
      }
      tokens.push({ type: 'IDENT', value: idStr });
      i = j;
      continue;
    }
    const two = input.substr(i, 2);
    if (TWO_CHAR_OPS.includes(two)) {
      tokens.push({ type: 'OP', value: two });
      i += 2;
      continue;
    }
    if (ONE_CHAR_OPS.includes(ch)) {
      tokens.push({ type: 'OP', value: ch });
      i++;
      continue;
    }
    // Unknown character — skip it silently rather than throwing, keeps
    // the editor forgiving of stray characters while typing.
    i++;
  }
  tokens.push({ type: 'EOF', value: null });
  return tokens;
}

// ---------------------------------------------------------------------------
// Parser (recursive descent). Precedence, tightest to loosest:
//   primary/() > ^ (right-assoc) > unary +/- > * / > + - > & (concat) > comparisons
// ---------------------------------------------------------------------------

class ParseError extends Error {}

class Parser {
  constructor(tokens) {
    this.tokens = tokens;
    this.pos = 0;
  }
  peek(offset = 0) {
    return this.tokens[this.pos + offset] || { type: 'EOF', value: null };
  }
  next() {
    return this.tokens[this.pos++];
  }
  isOp(value, offset = 0) {
    const t = this.peek(offset);
    return t.type === 'OP' && t.value === value;
  }
  expectOp(value) {
    if (!this.isOp(value)) throw new ParseError(`Expected '${value}'`);
    return this.next();
  }

  parseExpression() {
    return this.parseComparison();
  }

  parseComparison() {
    let left = this.parseConcat();
    while (['=', '<>', '<', '>', '<=', '>='].some((op) => this.isOp(op))) {
      const op = this.next().value;
      const right = this.parseConcat();
      left = { type: 'Binary', op, left, right };
    }
    return left;
  }

  parseConcat() {
    let left = this.parseAdditive();
    while (this.isOp('&')) {
      this.next();
      const right = this.parseAdditive();
      left = { type: 'Binary', op: '&', left, right };
    }
    return left;
  }

  parseAdditive() {
    let left = this.parseMultiplicative();
    while (this.isOp('+') || this.isOp('-')) {
      const op = this.next().value;
      const right = this.parseMultiplicative();
      left = { type: 'Binary', op, left, right };
    }
    return left;
  }

  parseMultiplicative() {
    let left = this.parseUnary();
    while (this.isOp('*') || this.isOp('/')) {
      const op = this.next().value;
      const right = this.parseUnary();
      left = { type: 'Binary', op, left, right };
    }
    return left;
  }

  parseUnary() {
    if (this.isOp('-') || this.isOp('+')) {
      const op = this.next().value;
      const expr = this.parseUnary();
      return { type: 'Unary', op, expr };
    }
    return this.parsePower();
  }

  parsePower() {
    let left = this.parsePrimary();
    while (this.isOp('^')) {
      this.next();
      const right = this.parseUnary();
      left = { type: 'Binary', op: '^', left, right };
    }
    return left;
  }

  parsePrimary() {
    const t = this.peek();
    if (t.type === 'NUMBER') {
      this.next();
      return { type: 'Number', value: t.value };
    }
    if (t.type === 'STRING') {
      this.next();
      return { type: 'String', value: t.value };
    }
    if (this.isOp('(')) {
      this.next();
      const expr = this.parseExpression();
      this.expectOp(')');
      return expr;
    }
    if (t.type === 'IDENT') {
      this.next();
      if (this.isOp('(')) {
        this.next();
        const args = [];
        if (!this.isOp(')')) {
          args.push(this.parseExpression());
          while (this.isOp(',')) {
            this.next();
            args.push(this.parseExpression());
          }
        }
        this.expectOp(')');
        return { type: 'Call', name: t.value, args };
      }
      if (isCellRefToken(t.value)) {
        if (this.isOp(':') && this.peek(1).type === 'IDENT' && isCellRefToken(this.peek(1).value)) {
          this.next();
          const t2 = this.next();
          return { type: 'Range', ref: `${t.value}:${t2.value}` };
        }
        return { type: 'Cell', ref: t.value };
      }
      if (/^TRUE$/i.test(t.value)) return { type: 'Boolean', value: true };
      if (/^FALSE$/i.test(t.value)) return { type: 'Boolean', value: false };
      return { type: 'Name', value: t.value };
    }
    throw new ParseError('Unexpected token');
  }
}

/** Parse a formula string (without the leading "=") into an AST. Throws ParseError on malformed input. */
export function parseFormula(input) {
  const tokens = tokenize(input);
  const parser = new Parser(tokens);
  const ast = parser.parseExpression();
  if (parser.peek().type !== 'EOF') throw new ParseError('Unexpected trailing input');
  return ast;
}

// ---------------------------------------------------------------------------
// Dependency collection
// ---------------------------------------------------------------------------

/** Walk an AST and collect the set of plain (unlocked) cell keys it reads. */
export function collectRefs(node, out = new Set()) {
  if (!node) return out;
  switch (node.type) {
    case 'Cell': {
      const parsed = parseCellRefStr(node.ref);
      if (parsed) out.add(cellKeyFromRC(parsed.col, parsed.row));
      break;
    }
    case 'Range': {
      const [a, b] = node.ref.split(':');
      const pa = parseCellRefStr(a);
      const pb = parseCellRefStr(b);
      if (pa && pb) {
        const range = {
          startCol: Math.min(pa.col, pb.col),
          endCol: Math.max(pa.col, pb.col),
          startRow: Math.min(pa.row, pb.row),
          endRow: Math.max(pa.row, pb.row),
        };
        for (const key of iterRangeKeys(range)) out.add(key);
      }
      break;
    }
    case 'Unary':
      collectRefs(node.expr, out);
      break;
    case 'Binary':
      collectRefs(node.left, out);
      collectRefs(node.right, out);
      break;
    case 'Call':
      for (const a of node.args) collectRefs(a, out);
      break;
    default:
      break;
  }
  return out;
}

// ---------------------------------------------------------------------------
// Evaluation
// ---------------------------------------------------------------------------

function toNumber(v) {
  if (isError(v)) return v;
  if (typeof v === 'number') return v;
  if (typeof v === 'boolean') return v ? 1 : 0;
  if (v === null || v === undefined || v === '') return 0;
  if (typeof v === 'string') {
    const trimmed = v.trim();
    if (trimmed === '') return 0;
    const n = Number(trimmed);
    if (Number.isNaN(n)) return new FormulaError('#VALUE!');
    return n;
  }
  return new FormulaError('#VALUE!');
}

function toStringVal(v) {
  if (isError(v)) return v;
  if (v === null || v === undefined) return '';
  if (typeof v === 'boolean') return v ? 'TRUE' : 'FALSE';
  return String(v);
}

function toBool(v) {
  if (isError(v)) return v;
  if (typeof v === 'boolean') return v;
  if (typeof v === 'number') return v !== 0;
  if (typeof v === 'string') {
    if (/^true$/i.test(v)) return true;
    if (/^false$/i.test(v)) return false;
    return new FormulaError('#VALUE!');
  }
  return new FormulaError('#VALUE!');
}

function scalarOf(v) {
  if (Array.isArray(v)) return v.length ? v[0] : '';
  return v;
}

function flatten(argVals) {
  const out = [];
  for (const v of argVals) {
    if (Array.isArray(v)) out.push(...v);
    else out.push(v);
  }
  return out;
}

function numericCoerce(v) {
  // Best-effort numeric coercion used by aggregate functions (SUM/AVERAGE/...):
  // numbers count, numeric-looking strings count, everything else is skipped.
  if (typeof v === 'number') return v;
  if (typeof v === 'boolean') return v ? 1 : 0;
  if (typeof v === 'string') {
    const trimmed = v.trim();
    if (trimmed === '') return null;
    const n = Number(trimmed);
    return Number.isNaN(n) ? null : n;
  }
  return null;
}

const FUNCTIONS = {
  SUM: (args) => {
    const flat = flatten(args);
    let sum = 0;
    for (const v of flat) {
      if (isError(v)) return v;
      const n = numericCoerce(v);
      if (n !== null) sum += n;
    }
    return sum;
  },
  AVERAGE: (args) => {
    const flat = flatten(args);
    let sum = 0;
    let count = 0;
    for (const v of flat) {
      if (isError(v)) return v;
      const n = numericCoerce(v);
      if (n !== null) {
        sum += n;
        count++;
      }
    }
    if (count === 0) return new FormulaError('#DIV/0!');
    return sum / count;
  },
  MIN: (args) => {
    const flat = flatten(args);
    let best = null;
    for (const v of flat) {
      if (isError(v)) return v;
      const n = numericCoerce(v);
      if (n !== null && (best === null || n < best)) best = n;
    }
    return best === null ? 0 : best;
  },
  MAX: (args) => {
    const flat = flatten(args);
    let best = null;
    for (const v of flat) {
      if (isError(v)) return v;
      const n = numericCoerce(v);
      if (n !== null && (best === null || n > best)) best = n;
    }
    return best === null ? 0 : best;
  },
  COUNT: (args) => {
    const flat = flatten(args);
    let count = 0;
    for (const v of flat) {
      if (isError(v)) return v;
      if (numericCoerce(v) !== null) count++;
    }
    return count;
  },
  COUNTA: (args) => {
    const flat = flatten(args);
    let count = 0;
    for (const v of flat) {
      if (isError(v)) return v;
      if (v !== '' && v !== null && v !== undefined) count++;
    }
    return count;
  },
  CONCAT: (args) => {
    const flat = flatten(args);
    let out = '';
    for (const v of flat) {
      if (isError(v)) return v;
      out += toStringVal(v);
    }
    return out;
  },
  ROUND: (args) => {
    const n = toNumber(scalarOf(args[0]));
    if (isError(n)) return n;
    const d = args.length > 1 ? toNumber(scalarOf(args[1])) : 0;
    if (isError(d)) return d;
    const factor = Math.pow(10, d);
    return Math.round(n * factor) / factor;
  },
  ABS: (args) => {
    const n = toNumber(scalarOf(args[0]));
    if (isError(n)) return n;
    return Math.abs(n);
  },
  POWER: (args) => {
    const base = toNumber(scalarOf(args[0]));
    if (isError(base)) return base;
    const exp = toNumber(scalarOf(args[1]));
    if (isError(exp)) return exp;
    return Math.pow(base, exp);
  },
  SQRT: (args) => {
    const n = toNumber(scalarOf(args[0]));
    if (isError(n)) return n;
    if (n < 0) return new FormulaError('#VALUE!');
    return Math.sqrt(n);
  },
  LEN: (args) => {
    const s = toStringVal(scalarOf(args[0]));
    if (isError(s)) return s;
    return s.length;
  },
  UPPER: (args) => {
    const s = toStringVal(scalarOf(args[0]));
    if (isError(s)) return s;
    return s.toUpperCase();
  },
  LOWER: (args) => {
    const s = toStringVal(scalarOf(args[0]));
    if (isError(s)) return s;
    return s.toLowerCase();
  },
  TRIM: (args) => {
    const s = toStringVal(scalarOf(args[0]));
    if (isError(s)) return s;
    return s.replace(/\s+/g, ' ').trim();
  },
  MOD: (args) => {
    const n = toNumber(scalarOf(args[0]));
    if (isError(n)) return n;
    const d = toNumber(scalarOf(args[1]));
    if (isError(d)) return d;
    if (d === 0) return new FormulaError('#DIV/0!');
    return n - d * Math.floor(n / d);
  },
  INT: (args) => {
    const n = toNumber(scalarOf(args[0]));
    if (isError(n)) return n;
    return Math.floor(n);
  },
  TODAY: () => Math.floor(Date.now() / 86400000),
  NOW: () => Date.now() / 86400000,
  PI: () => Math.PI,
};
FUNCTIONS.CONCATENATE = FUNCTIONS.CONCAT;

function evalBinary(node, ctx) {
  const op = node.op;
  if (op === '&') {
    const l = toStringVal(scalarOf(evaluate(node.left, ctx)));
    if (isError(l)) return l;
    const r = toStringVal(scalarOf(evaluate(node.right, ctx)));
    if (isError(r)) return r;
    return l + r;
  }
  if (['=', '<>', '<', '>', '<=', '>='].includes(op)) {
    let l = scalarOf(evaluate(node.left, ctx));
    if (isError(l)) return l;
    let r = scalarOf(evaluate(node.right, ctx));
    if (isError(r)) return r;
    let cmp;
    if (typeof l === 'number' && typeof r === 'number') {
      cmp = l < r ? -1 : l > r ? 1 : 0;
    } else if (typeof l === 'boolean' || typeof r === 'boolean') {
      const lb = l === true || l === 'TRUE';
      const rb = r === true || r === 'TRUE';
      cmp = lb === rb ? 0 : lb ? 1 : -1;
    } else {
      const ls = String(l);
      const rs = String(r);
      cmp = ls < rs ? -1 : ls > rs ? 1 : 0;
    }
    switch (op) {
      case '=':
        return cmp === 0;
      case '<>':
        return cmp !== 0;
      case '<':
        return cmp < 0;
      case '>':
        return cmp > 0;
      case '<=':
        return cmp <= 0;
      case '>=':
        return cmp >= 0;
      default:
        return new FormulaError('#VALUE!');
    }
  }
  // arithmetic
  const l = toNumber(scalarOf(evaluate(node.left, ctx)));
  if (isError(l)) return l;
  const r = toNumber(scalarOf(evaluate(node.right, ctx)));
  if (isError(r)) return r;
  switch (op) {
    case '+':
      return l + r;
    case '-':
      return l - r;
    case '*':
      return l * r;
    case '/':
      if (r === 0) return new FormulaError('#DIV/0!');
      return l / r;
    case '^':
      return Math.pow(l, r);
    default:
      return new FormulaError('#VALUE!');
  }
}

function evalCall(node, ctx) {
  const name = node.name.toUpperCase();
  if (name === 'IF') {
    if (node.args.length < 2) return new FormulaError('#VALUE!');
    const condVal = scalarOf(evaluate(node.args[0], ctx));
    if (isError(condVal)) return condVal;
    const cond = toBool(condVal);
    if (isError(cond)) return cond;
    if (cond) return evaluate(node.args[1], ctx);
    return node.args.length > 2 ? evaluate(node.args[2], ctx) : false;
  }
  if (name === 'AND' || name === 'OR') {
    let result = name === 'AND';
    for (const a of node.args) {
      const v = evaluate(a, ctx);
      if (isError(v)) return v;
      const vals = Array.isArray(v) ? v : [v];
      for (const vv of vals) {
        const b = toBool(vv);
        if (isError(b)) return b;
        result = name === 'AND' ? result && b : result || b;
      }
    }
    return result;
  }
  if (name === 'NOT') {
    const v = scalarOf(evaluate(node.args[0], ctx));
    if (isError(v)) return v;
    const b = toBool(v);
    if (isError(b)) return b;
    return !b;
  }
  const fn = FUNCTIONS[name];
  if (!fn) return new FormulaError('#NAME?');
  const argVals = [];
  for (const a of node.args) {
    const v = evaluate(a, ctx);
    if (isError(v)) return v;
    argVals.push(v);
  }
  return fn(argVals);
}

/** Evaluate an AST node against a context providing getCellValue/getRange. */
export function evaluate(node, ctx) {
  if (!node) return new FormulaError('#VALUE!');
  switch (node.type) {
    case 'Number':
      return node.value;
    case 'String':
      return node.value;
    case 'Boolean':
      return node.value;
    case 'Cell':
      return ctx.getCellValue(node.ref);
    case 'Range':
      return ctx.getRange(node.ref);
    case 'Unary': {
      const v = toNumber(scalarOf(evaluate(node.expr, ctx)));
      if (isError(v)) return v;
      return node.op === '-' ? -v : v;
    }
    case 'Binary':
      return evalBinary(node, ctx);
    case 'Call':
      return evalCall(node, ctx);
    case 'Name':
      return new FormulaError('#NAME?');
    default:
      return new FormulaError('#VALUE!');
  }
}

export { ParseError };
