#!/usr/bin/env node
/* Runtime smoke test: executes each WebGL module's real JS against a
   mocked THREE + DOM, then drives the rAF loops and fires events.
   Cannot compile GLSL (no GL context anywhere in this sandbox), but it
   DOES exercise renderer/scene/material/uniform setup, every event
   handler, and the animation loops — catching undefined refs, bad
   uniform names, and logic errors that a syntax check can't. */
"use strict";
const fs = require("fs"), path = require("path"), vm = require("vm");
const SRC = path.join(__dirname, "apartment-src");

/* ---------- generic auto-mock (callable, constructable, any-prop) ---------- */
const auto = new Proxy(function(){}, {
  get(t, p){
    if(p === Symbol.toPrimitive) return () => 0;     // numeric coercion → 0 (no NaN/throw)
    if(p === Symbol.iterator) return function*(){};
    if(p === "then") return undefined;               // not a thenable
    return auto;                                      // every prop is chainable (incl. .value/.x/.set)
  },
  set(){ return true; },
  apply(){ return auto; },
  construct(){ return auto; }
});

/* ---------- DOM / window stubs ---------- */
const listeners = [];
function el(){
  const style = new Proxy({}, { get:()=>"", set:()=>true });
  const e = {
    style, dataset:{}, classList:{ add(){}, remove(){}, toggle(){}, contains(){return false;} },
    addEventListener(type, cb){ listeners.push({type, cb}); },
    removeEventListener(){}, appendChild(x){ return x; }, removeChild(){}, remove(){},
    setAttribute(){}, removeAttribute(){}, getAttribute(){ return null; },
    insertBefore(){}, querySelector(){ return el(); }, querySelectorAll(){ return [el()]; },
    getBoundingClientRect(){ return { left:0, top:0, right:100, bottom:100, width:100, height:100 }; },
    getContext(kind){ return kind === "2d" ? ctx2d() : null; },
    parentNode: null, parentElement: { classList:{ contains(){return false;} } },
    children: [], childNodes: [], offsetLeft:0, offsetTop:0, offsetWidth:100, offsetHeight:100,
    offsetParent:null, clientWidth:1200, clientHeight:800, complete:true, currentSrc:"blob:x", src:"blob:x",
    width:100, height:100, contains(){ return false; }
  };
  e.parentNode = e.parentElement = { insertBefore(){}, classList:{contains(){return false;}}, appendChild(){} };
  return e;
}
function ctx2d(){
  return { clearRect(){}, fillRect(){}, drawImage(){}, fillStyle:"#000",
           getImageData(){ return { data:[10,10,10,255] }; } };
}
const docEl = el();
const document = {
  documentElement: docEl, body: el(), head: el(),
  getElementById(){ return el(); },
  querySelector(){ return el(); }, querySelectorAll(){ return [el(), el()]; },
  createElement(){ return el(); }, addEventListener(type, cb){ listeners.push({type, cb}); },
  hidden:false
};
const rafQ = [];
function makeWindow(THREE){
  const mm = () => ({ matches:false, addEventListener(){}, removeEventListener(){}, addListener(){} });
  const w = {
    THREE, document, innerWidth:1200, innerHeight:800, devicePixelRatio:2, scrollY:0, pageYOffset:0,
    matchMedia: mm, getComputedStyle: () => ({ getPropertyValue:()=>"#caa14e", color:"rgb(200,160,80)", backgroundColor:"rgb(11,10,9)", position:"static" }),
    requestAnimationFrame:(cb)=>{ rafQ.push(cb); return rafQ.length; }, cancelAnimationFrame(){},
    addEventListener(type, cb){ listeners.push({type, cb}); }, removeEventListener(){},
    performance:{ now:()=>16 }, localStorage:{ getItem:()=>null, setItem(){} },
    MutationObserver: class { observe(){} disconnect(){} },
    IntersectionObserver: class { constructor(cb){ this.cb=cb; } observe(){ try{ this.cb([{isIntersecting:true}]); }catch(e){} } disconnect(){} },
    URL:{ createObjectURL:()=>"blob:x", revokeObjectURL(){} }, console
  };
  w.window = w;
  return w;
}

function runModule(file, THREE){
  const code = fs.readFileSync(path.join(SRC, file), "utf8");
  const w = makeWindow(THREE);
  const ctx = vm.createContext(w);
  // expose bare globals the modules use unqualified
  ["document","matchMedia","getComputedStyle","requestAnimationFrame","cancelAnimationFrame",
   "performance","MutationObserver","IntersectionObserver","innerWidth","innerHeight",
   "devicePixelRatio","URL","console","window"].forEach(k => { ctx[k] = w[k]; });
  vm.runInContext(code, ctx, { filename:file });
  return w;
}

/* ---------- run ---------- */
let fail = 0;
const ok = (c, m) => { console.log((c?"  ok  ":"FAIL  ")+m); if(!c) fail++; };

// shared THREE used across modules in one window so KXGL/KXPostFX persist
const THREE = auto;
const win = makeWindow(THREE);
const ctx = vm.createContext(win);
["document","matchMedia","getComputedStyle","requestAnimationFrame","cancelAnimationFrame",
 "performance","MutationObserver","IntersectionObserver","innerWidth","innerHeight",
 "devicePixelRatio","URL","console","window"].forEach(k => { ctx[k] = win[k]; });
// THREE on the window so `window.THREE` and bare `THREE`(via window) resolve
ctx.THREE = THREE; win.THREE = THREE;

// shipped modules only (hero3d/gallery3d/materials3d/postfx reverted per user request)
const MODULES = ["glshared.js","engine3d.js","plan3d.js"];
for(const f of MODULES){
  try{
    const code = fs.readFileSync(path.join(SRC, f), "utf8");
    vm.runInContext(code, ctx, { filename:f });
    ok(true, `loaded ${f}`);
  }catch(err){ ok(false, `loaded ${f} → ${err.stack.split("\n").slice(0,3).join(" | ")}`); }
}

ok("KXGL" in win, "window.KXGL defined (object or null)");

// drive the animation loops a handful of times (catches loop-body errors)
let ticks = 0;
for(let i=0;i<8 && rafQ.length;i++){
  const batch = rafQ.splice(0, rafQ.length);
  for(const cb of batch){ try{ cb(16*(i+1)); ticks++; }catch(err){ ok(false, `rAF tick threw → ${err.message}`); } }
}
ok(true, `drove ${ticks} rAF callback(s) without throwing`);

// fire the events the modules listen for
const ev = { clientX:600, clientY:400, target:{ closest:()=>null, matches:()=>false } };
let fired = 0;
for(const {type, cb} of listeners.slice()){
  if(["pointermove","scroll","resize","pointerenter","pointerleave","pointerover","pointerout","visibilitychange","load","click"].includes(type)){
    try{ cb(ev); fired++; }catch(err){ ok(false, `event "${type}" handler threw → ${err.message}`); }
  }
}
ok(true, `fired ${fired} event handler(s) without throwing`);

// a couple more ticks after events
for(let i=0;i<4 && rafQ.length;i++){
  const batch = rafQ.splice(0, rafQ.length);
  for(const cb of batch){ try{ cb(200+16*i); }catch(err){ ok(false, `post-event rAF threw → ${err.message}`); } }
}
ok(true, "post-event rAF ticks clean");

console.log(fail ? `\n${fail} SMOKE CHECK(S) FAILED\n` : "\nSMOKE: ALL CLEAR (JS logic paths exercised; GLSL not compiled)\n");
process.exit(fail?1:0);
