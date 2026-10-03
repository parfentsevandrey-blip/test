package api

import (
	"context"
	"sync"
	"sync/atomic"
	"testing"
	"time"
)

// The pixels being decoded at once are bounded, whatever the number of requests.
func TestThumbnailPixelBudget(t *testing.T) {
	b := &pixelBudget{free: 100}
	ctx := context.Background()
	if err := b.acquire(ctx, 60); err != nil {
		t.Fatal(err)
	}
	// 60 + 60 does not fit: the second waits...
	got := make(chan struct{})
	go func() {
		_ = b.acquire(ctx, 60)
		close(got)
	}()
	select {
	case <-got:
		t.Fatal("a request got pixels that were not free")
	case <-time.After(100 * time.Millisecond):
	}
	// ...but a small one fits next to the first.
	if err := b.acquire(ctx, 40); err != nil {
		t.Fatal(err)
	}
	b.release(40)
	b.release(60)
	select {
	case <-got:
	case <-time.After(time.Second):
		t.Fatal("the waiting request was not woken up when pixels were freed")
	}
	b.release(60)
	if b.free != 100 {
		t.Fatalf("%d pixels free at the end, want 100", b.free)
	}

	// A waiter whose client went away stops waiting.
	if err := b.acquire(ctx, 100); err != nil {
		t.Fatal(err)
	}
	cctx, cancel := context.WithTimeout(ctx, 50*time.Millisecond)
	defer cancel()
	if err := b.acquire(cctx, 10); err == nil {
		t.Fatal("a cancelled request was served")
	}
	b.release(100)

	// Many at once never hold more than the budget.
	var inFlight, peak atomic.Int64
	var wg sync.WaitGroup
	for i := 0; i < 50; i++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			const n = 30
			if err := b.acquire(ctx, n); err != nil {
				return
			}
			cur := inFlight.Add(n)
			for {
				p := peak.Load()
				if cur <= p || peak.CompareAndSwap(p, cur) {
					break
				}
			}
			time.Sleep(2 * time.Millisecond)
			inFlight.Add(-n)
			b.release(n)
		}()
	}
	wg.Wait()
	if peak.Load() > 100 {
		t.Fatalf("%d pixels were in flight at once, the budget is 100", peak.Load())
	}
	if maxThumbInFlight < maxThumbPixels {
		t.Fatal("the budget is smaller than the largest image that is allowed: it could never be decoded")
	}
}
