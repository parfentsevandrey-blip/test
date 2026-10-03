package diskfree

import "testing"

func TestFreeReportsSomethingForAnExistingDirectory(t *testing.T) {
	free, ok := Free(t.TempDir())
	if !ok {
		t.Skip("free space is not available on this platform")
	}
	if free == 0 {
		t.Fatal("a writable temporary directory reports no free space at all")
	}
}

func TestFreeIsUnknownForAMissingDirectory(t *testing.T) {
	if _, ok := Free(t.TempDir() + "/does/not/exist"); ok {
		t.Fatal("a missing directory must not report a size")
	}
}
