// Package store is a thin typed wrapper over bbolt, the embedded key/value
// database that holds mail, chat and transfer records.
package store

import (
	"encoding/json"
	"errors"
	"fmt"
	"time"

	bolt "go.etcd.io/bbolt"
)

// DB is an open database.
type DB struct{ db *bolt.DB }

// Open opens (creating if needed) the database file. It fails fast if another
// process already holds it, which usually means themesh is already running.
func Open(path string) (*DB, error) {
	db, err := bolt.Open(path, 0o600, &bolt.Options{Timeout: 3 * time.Second})
	if err != nil {
		if errors.Is(err, bolt.ErrTimeout) {
			return nil, fmt.Errorf("store: %s is locked by another process (is themesh already running?)", path)
		}
		return nil, err
	}
	return &DB{db: db}, nil
}

// Close closes the database.
func (d *DB) Close() error { return d.db.Close() }

// PutJSON stores v as JSON under key in the named bucket.
func (d *DB) PutJSON(bucket, key string, v any) error {
	raw, err := json.Marshal(v)
	if err != nil {
		return err
	}
	return d.db.Update(func(tx *bolt.Tx) error {
		b, err := tx.CreateBucketIfNotExists([]byte(bucket))
		if err != nil {
			return err
		}
		return b.Put([]byte(key), raw)
	})
}

// GetJSON loads key into v; ok is false if it does not exist.
func (d *DB) GetJSON(bucket, key string, v any) (ok bool, err error) {
	err = d.db.View(func(tx *bolt.Tx) error {
		b := tx.Bucket([]byte(bucket))
		if b == nil {
			return nil
		}
		raw := b.Get([]byte(key))
		if raw == nil {
			return nil
		}
		ok = true
		return json.Unmarshal(raw, v)
	})
	return ok, err
}

// Delete removes a key.
func (d *DB) Delete(bucket, key string) error {
	return d.db.Update(func(tx *bolt.Tx) error {
		b := tx.Bucket([]byte(bucket))
		if b == nil {
			return nil
		}
		return b.Delete([]byte(key))
	})
}

// ForEach calls f for every key in the bucket (in key order). Returning a
// non-nil error stops the iteration.
func (d *DB) ForEach(bucket string, f func(key string, raw []byte) error) error {
	return d.db.View(func(tx *bolt.Tx) error {
		b := tx.Bucket([]byte(bucket))
		if b == nil {
			return nil
		}
		return b.ForEach(func(k, v []byte) error { return f(string(k), v) })
	})
}

// Batch runs several writes in one transaction.
func (d *DB) Batch(f func(tx *Tx) error) error {
	return d.db.Update(func(btx *bolt.Tx) error { return f(&Tx{tx: btx}) })
}

// Tx is a write transaction.
type Tx struct{ tx *bolt.Tx }

// PutJSON stores v under key.
func (t *Tx) PutJSON(bucket, key string, v any) error {
	raw, err := json.Marshal(v)
	if err != nil {
		return err
	}
	b, err := t.tx.CreateBucketIfNotExists([]byte(bucket))
	if err != nil {
		return err
	}
	return b.Put([]byte(key), raw)
}

// Delete removes a key.
func (t *Tx) Delete(bucket, key string) error {
	b := t.tx.Bucket([]byte(bucket))
	if b == nil {
		return nil
	}
	return b.Delete([]byte(key))
}
