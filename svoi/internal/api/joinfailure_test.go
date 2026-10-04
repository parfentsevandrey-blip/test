package api

import (
	"errors"
	"fmt"
	"testing"

	"github.com/parfentsevandrey-blip/test/svoi/internal/mesh"
)

// What the interface is told when a join fails: each reason has its own code, because the advice differs. A device that does
// not answer is not a bad code.
func TestJoinFailureCodes(t *testing.T) {
	for _, tc := range []struct {
		err  error
		code string
	}{
		{fmt.Errorf("%w (timeout; tried 192.168.1.2:41710)", mesh.ErrInviterUnreachable), "offline"},
		{fmt.Errorf("%w; ask for a new one", mesh.ErrInviteExpired), "expired"},
		{fmt.Errorf("%w: invitation not valid", mesh.ErrJoinRefused), "denied"},
		{errors.New("identity: this is not an invitation to The Mesh"), "invalid"},
	} {
		var ae *apiError
		if !errors.As(joinFailure(tc.err), &ae) || ae.Code != tc.code || ae.Msg != tc.err.Error() {
			t.Errorf("%v: got %+v, want code %q with the message kept", tc.err, ae, tc.code)
		}
		if _, ok := statusFor[tc.code]; !ok {
			t.Errorf("code %q has no HTTP status", tc.code)
		}
	}
}
