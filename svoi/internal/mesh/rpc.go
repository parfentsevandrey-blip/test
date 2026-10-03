package mesh

import (
	"bytes"
	"context"
	"encoding/binary"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"os"
	"time"

	"github.com/quic-go/quic-go"
)

// Every application protocol rides on QUIC streams with a tiny framing:
//
//	request:   [u32 len][JSON {"m": method, "a": args}]  ... optional raw body
//	response:  [u32 len][JSON {"ok": true, "r": result} | {"ok": false, "e": {...}}] ... optional raw body
//
// Simple calls exchange just the two frames. Bulk operations (file download,
// upload, TCP tunnels) continue with raw bytes on the same stream after the
// header, so there is no per-chunk overhead and QUIC's flow control does the
// back-pressure.

const maxFrame = 8 << 20

// Error codes carried in RPC failures.
const (
	CodeDenied      = "denied"
	CodeNotFound    = "notfound"
	CodeInvalid     = "invalid"
	CodeInternal    = "internal"
	CodeBusy        = "busy"
	CodeUnsupported = "unsupported"
	CodeOffline     = "offline"
	CodeExists      = "exists"
	CodeTooLarge    = "toolarge"
)

// RPCError is an error returned by the remote side of a call.
type RPCError struct {
	Code string `json:"c"`
	Msg  string `json:"m"`
}

func (e *RPCError) Error() string { return e.Code + ": " + e.Msg }

// Errf builds an RPCError for handlers to return.
func Errf(code, format string, args ...any) *RPCError {
	return &RPCError{Code: code, Msg: fmt.Sprintf(format, args...)}
}

// IsCode reports whether err is an RPCError with the given code.
func IsCode(err error, code string) bool {
	var re *RPCError
	return errors.As(err, &re) && re.Code == code
}

type requestFrame struct {
	Method string          `json:"m"`
	Args   json.RawMessage `json:"a,omitempty"`
}

type responseFrame struct {
	OK     bool            `json:"ok"`
	Result json.RawMessage `json:"r,omitempty"`
	Err    *RPCError       `json:"e,omitempty"`
}

func writeFrame(w io.Writer, v any) error {
	b, err := json.Marshal(v)
	if err != nil {
		return err
	}
	if len(b) > maxFrame {
		return errors.New("mesh: frame too large")
	}
	out := make([]byte, 4+len(b))
	binary.BigEndian.PutUint32(out, uint32(len(b)))
	copy(out[4:], b)
	_, err = w.Write(out)
	return err
}

func readFrame(r io.Reader, v any) error { return readFrameMax(r, v, maxFrame) }

// readFrameMax reads one length-prefixed JSON frame of at most limit bytes. The
// buffer grows with the bytes that actually arrive: a peer that announces
// 8 MiB and then stays silent costs a few bytes, not 8 MiB per open stream.
func readFrameMax(r io.Reader, v any, limit uint32) error {
	var hdr [4]byte
	if _, err := io.ReadFull(r, hdr[:]); err != nil {
		return err
	}
	n := binary.BigEndian.Uint32(hdr[:])
	if n > limit {
		return errors.New("mesh: frame too large")
	}
	var buf bytes.Buffer
	if n <= 16<<10 {
		buf.Grow(int(n))
	}
	if _, err := io.CopyN(&buf, r, int64(n)); err != nil {
		return err
	}
	return json.Unmarshal(buf.Bytes(), v)
}

// Call is an incoming request as seen by a handler.
type Call struct {
	Peer   *Peer
	Method string
	Args   json.RawMessage
}

// Decode unmarshals the call arguments into v.
func (c *Call) Decode(v any) error {
	if len(c.Args) == 0 {
		return nil
	}
	if err := json.Unmarshal(c.Args, v); err != nil {
		return Errf(CodeInvalid, "bad arguments: %v", err)
	}
	return nil
}

// Handler serves a simple request/response method.
type Handler func(ctx context.Context, c *Call) (any, error)

// StreamHandler serves a method that continues with raw bytes on the stream.
// It must call s.Reply (or s.Fail) before writing body bytes.
type StreamHandler func(ctx context.Context, c *Call, s *ServerStream) error

// ServerStream is the server's end of a streaming call.
type ServerStream struct {
	s       *quic.Stream
	replied bool
}

// Read reads the request body that follows the header.
func (s *ServerStream) Read(p []byte) (int, error) { return s.s.Read(p) }

// Write writes response body bytes (after Reply).
func (s *ServerStream) Write(p []byte) (int, error) { return s.s.Write(p) }

// Reply sends the response header; body bytes may follow.
func (s *ServerStream) Reply(result any) error {
	if s.replied {
		return errors.New("mesh: already replied")
	}
	s.replied = true
	var raw json.RawMessage
	if result != nil {
		b, err := json.Marshal(result)
		if err != nil {
			return err
		}
		raw = b
	}
	return writeFrame(s.s, responseFrame{OK: true, Result: raw})
}

// Fail sends an error response.
func (s *ServerStream) Fail(err error) error {
	if s.replied {
		return nil
	}
	s.replied = true
	return writeFrame(s.s, responseFrame{Err: asRPCError(err)})
}

// CloseWrite ends the response body (half-close); reads stay possible.
func (s *ServerStream) CloseWrite() error { return s.s.Close() }

// SetDeadline bounds the whole stream.
func (s *ServerStream) SetDeadline(t time.Time) error { return s.s.SetDeadline(t) }

func asRPCError(err error) *RPCError {
	var re *RPCError
	if errors.As(err, &re) {
		return re
	}
	if errors.Is(err, os.ErrNotExist) {
		return Errf(CodeNotFound, "not found")
	}
	if errors.Is(err, os.ErrPermission) {
		return Errf(CodeDenied, "permission denied")
	}
	if errors.Is(err, context.DeadlineExceeded) {
		return Errf(CodeBusy, "timed out")
	}
	return Errf(CodeInternal, "%v", err)
}

type handlerEntry struct {
	simple Handler
	stream StreamHandler
}

// Handle registers a request/response method.
func (n *Node) Handle(method string, h Handler) {
	n.hmu.Lock()
	n.handlers[method] = handlerEntry{simple: h}
	n.hmu.Unlock()
}

// HandleStream registers a streaming method.
func (n *Node) HandleStream(method string, h StreamHandler) {
	n.hmu.Lock()
	n.handlers[method] = handlerEntry{stream: h}
	n.hmu.Unlock()
}

// serveConn accepts streams on a member connection until it closes.
func (n *Node) serveConn(p *Peer, conn *quic.Conn) {
	for {
		s, err := conn.AcceptStream(conn.Context())
		if err != nil {
			return
		}
		go n.serveStream(p, s)
	}
}

func (n *Node) serveStream(p *Peer, s *quic.Stream) {
	defer func() {
		if r := recover(); r != nil {
			n.log.Error("handler panic", "panic", r)
			s.CancelRead(1)
			s.CancelWrite(1)
		}
	}()
	_ = s.SetReadDeadline(time.Now().Add(15 * time.Second))
	var req requestFrame
	if err := readFrame(s, &req); err != nil {
		s.CancelRead(2)
		s.CancelWrite(2)
		return
	}
	_ = s.SetReadDeadline(time.Time{})
	p.touch()

	n.hmu.RLock()
	h, ok := n.handlers[req.Method]
	n.hmu.RUnlock()
	if !ok {
		_ = writeFrame(s, responseFrame{Err: Errf(CodeUnsupported, "unknown method %q", req.Method)})
		_ = s.Close()
		return
	}
	ctx, cancel := context.WithCancel(s.Context())
	defer cancel()
	call := &Call{Peer: p, Method: req.Method, Args: req.Args}

	if h.simple != nil {
		res, err := h.simple(ctx, call)
		var resp responseFrame
		if err != nil {
			resp.Err = asRPCError(err)
		} else {
			resp.OK = true
			if res != nil {
				b, merr := json.Marshal(res)
				if merr != nil {
					resp = responseFrame{Err: Errf(CodeInternal, "encode result: %v", merr)}
				} else {
					resp.Result = b
				}
			}
		}
		_ = writeFrame(s, resp)
		_ = s.Close()
		s.CancelRead(0)
		return
	}
	ss := &ServerStream{s: s}
	if err := h.stream(ctx, call, ss); err != nil {
		_ = ss.Fail(err)
		s.CancelRead(3)
		_ = s.Close()
		return
	}
	if !ss.replied {
		_ = ss.Reply(nil)
	}
	_ = s.Close()
}

// ---- client side ----

// ClientStream is the caller's end of a streaming call.
type ClientStream struct {
	s *quic.Stream
}

// Write sends request body bytes.
func (c *ClientStream) Write(p []byte) (int, error) { return c.s.Write(p) }

// CloseWrite signals the end of the request body.
func (c *ClientStream) CloseWrite() error { return c.s.Close() }

// Read reads response body bytes (after ReadResponse).
func (c *ClientStream) Read(p []byte) (int, error) { return c.s.Read(p) }

// Cancel aborts the call.
func (c *ClientStream) Cancel() {
	c.s.CancelRead(0)
	c.s.CancelWrite(0)
}

// Close ends the stream, discarding anything unread.
func (c *ClientStream) Close() error {
	c.s.CancelRead(0)
	return c.s.Close()
}

// SetDeadline bounds reads and writes.
func (c *ClientStream) SetDeadline(t time.Time) error { return c.s.SetDeadline(t) }

// ReadResponse reads the response header, decoding the result into out (if not
// nil). A remote failure is returned as *RPCError.
func (c *ClientStream) ReadResponse(out any) error {
	var resp responseFrame
	if err := readFrame(c.s, &resp); err != nil {
		return err
	}
	if !resp.OK {
		if resp.Err == nil {
			return errors.New("mesh: malformed response")
		}
		return resp.Err
	}
	if out != nil && len(resp.Result) > 0 {
		return json.Unmarshal(resp.Result, out)
	}
	return nil
}

// OpenStream starts a streaming call to the peer. The request header is sent
// immediately; the caller then writes any body, calls CloseWrite, and reads
// the response with ReadResponse.
func (p *Peer) OpenStream(ctx context.Context, method string, args any) (*ClientStream, error) {
	conn := p.currentConn()
	if conn == nil {
		return nil, Errf(CodeOffline, "%s is offline", p.Name())
	}
	s, err := conn.OpenStreamSync(ctx)
	if err != nil {
		return nil, err
	}
	var raw json.RawMessage
	if args != nil {
		b, err := json.Marshal(args)
		if err != nil {
			s.CancelRead(0)
			s.CancelWrite(0)
			return nil, err
		}
		raw = b
	}
	if err := writeFrame(s, requestFrame{Method: method, Args: raw}); err != nil {
		s.CancelRead(0)
		s.CancelWrite(0)
		return nil, err
	}
	p.touch()
	return &ClientStream{s: s}, nil
}

// Call performs a request/response call and decodes the result into out.
func (p *Peer) Call(ctx context.Context, method string, args, out any) error {
	if _, ok := ctx.Deadline(); !ok {
		var cancel context.CancelFunc
		ctx, cancel = context.WithTimeout(ctx, 30*time.Second)
		defer cancel()
	}
	cs, err := p.OpenStream(ctx, method, args)
	if err != nil {
		return err
	}
	stop := context.AfterFunc(ctx, cs.Cancel)
	defer stop()
	if err := cs.CloseWrite(); err != nil {
		return err
	}
	err = cs.ReadResponse(out)
	cs.Close()
	if err != nil && ctx.Err() != nil {
		return ctx.Err()
	}
	return err
}

// SendDatagram sends an unreliable datagram (used by the TUN data plane).
func (p *Peer) SendDatagram(b []byte) error {
	conn := p.currentConn()
	if conn == nil {
		return Errf(CodeOffline, "%s is offline", p.Name())
	}
	return conn.SendDatagram(b)
}

// DatagramHandler receives datagrams whose first byte equals the registered kind.
type DatagramHandler func(p *Peer, data []byte)

// HandleDatagram registers a handler for datagrams starting with kind.
func (n *Node) HandleDatagram(kind byte, h DatagramHandler) {
	n.hmu.Lock()
	n.dgram[kind] = h
	n.hmu.Unlock()
}

func (n *Node) serveDatagrams(p *Peer, conn *quic.Conn) {
	for {
		b, err := conn.ReceiveDatagram(conn.Context())
		if err != nil {
			return
		}
		if len(b) == 0 {
			continue
		}
		n.hmu.RLock()
		h := n.dgram[b[0]]
		n.hmu.RUnlock()
		if h != nil {
			h(p, b[1:])
		}
	}
}
