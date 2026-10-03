package main

import (
	"context"
	"errors"
	"flag"
	"fmt"
	"os"
	"time"

	"github.com/parfentsevandrey-blip/test/svoi/internal/app"
	"github.com/parfentsevandrey-blip/test/svoi/internal/mesh"
)

// openOffline opens the data directory without starting a UI. It refuses if a
// node is already running there (the database would be locked anyway).
func openOffline(dir string, debug bool) (*app.App, error) {
	if _, err := newClient(dir); err == nil {
		return nil, errors.New("svoi is running on this data directory; use the web interface or stop it first")
	}
	return app.Open(app.Options{Dir: dir, Logger: newLogger(debug, os.Stderr)})
}

func cmdInit(args []string) error {
	fs := flag.NewFlagSet("init", flag.ExitOnError)
	var cf commonFlags
	cf.register(fs)
	meshName := fs.String("mesh", "My mesh", "name of the new mesh")
	name := fs.String("name", "", "name of this device (default: hostname)")
	owner := fs.String("owner", "", "owner name")
	fs.Parse(args)
	a, err := openOffline(cf.dir, false)
	if err != nil {
		return err
	}
	defer a.Close()
	if a.Node().Configured() {
		return errors.New("this device already belongs to a mesh")
	}
	if err := a.CreateMesh(*meshName, *name, *owner); err != nil {
		return err
	}
	s := a.Node().Self()
	fmt.Printf("Created mesh %q. This device is %s (%s).\nStart it with `svoi up`, then add devices with `svoi invite`.\n", s.MeshName, s.Name, s.IP4)
	return nil
}

func cmdJoin(args []string) error {
	fs := flag.NewFlagSet("join", flag.ExitOnError)
	var cf commonFlags
	cf.register(fs)
	name := fs.String("name", "", "name of this device (default: hostname)")
	pos := parseInterspersed(fs, args)
	if len(pos) != 1 {
		return errors.New("usage: svoi join <invite code> [--name NAME]")
	}
	a, err := openOffline(cf.dir, false)
	if err != nil {
		return err
	}
	defer a.Close()
	if a.Node().Configured() {
		return errors.New("this device already belongs to a mesh")
	}
	ctx, cancel := context.WithTimeout(context.Background(), 45*time.Second)
	defer cancel()
	fmt.Println("Connecting to the inviting device…")
	if err := a.JoinMesh(ctx, pos[0], *name); err != nil {
		return err
	}
	s := a.Node().Self()
	fmt.Printf("Joined mesh %q as %s (%s).\nStart it with `svoi up`.\n", s.MeshName, s.Name, s.IP4)
	// Give the first sync a moment so the member list is persisted.
	time.Sleep(1500 * time.Millisecond)
	_ = mesh.Version
	return nil
}
