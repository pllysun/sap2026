// Run from third_party/go-judge with: go run ../../ops/security-audit/agent-source-canary-probe.go
// Uses the unmodified model/worker/envexec/filestore packages. No sandbox is run.
package main

import (
	"encoding/json"
	"fmt"
	"io"
	"os"
	"path/filepath"

	"github.com/criyle/go-judge/cmd/go-judge/model"
	"github.com/criyle/go-judge/envexec"
	"github.com/criyle/go-judge/filestore"
)

func main() {
	dir, err := os.MkdirTemp("", "sap-audit-model-")
	if err != nil { panic(err) }
	defer os.RemoveAll(dir)
	canary := filepath.Join(dir, "external-canary.txt")
	const marker = "AUDIT_SYNTHETIC_CANARY_2026"
	if err := os.WriteFile(canary, []byte(marker), 0600); err != nil { panic(err) }
	raw := fmt.Sprintf(`{"cmd":[{"args":["/usr/bin/cat"],"copyIn":{"audit-canary":{"src":%q}},"files":[{"content":""},{"name":"stdout","max":4096}]}]}`, canary)
	var input model.Request
	if err := json.Unmarshal([]byte(raw), &input); err != nil { panic(err) }
	request, err := model.ConvertRequest(&input, nil)
	if err != nil { panic(err) }
	file, err := request.Cmd[0].CopyIn["audit-canary"].EnvFile(nil)
	if err != nil { panic(err) }
	reader, err := envexec.FileToReader(file)
	if err != nil { panic(err) }
	data, err := io.ReadAll(reader)
	reader.Close()
	if err != nil { panic(err) }
	_, constrainedErr := model.ConvertRequest(&input, []string{filepath.Join(dir, "allowed-inputs")})
	store := filestore.NewFileLocalStore(dir)
	_, traversal := store.Get("../external-canary.txt")
	result := map[string]any{
		"scope": "actual unmodified Go request conversion and host FileToReader; only synthetic temp canary; no code execution",
		"src_without_prefix_accepted": true,
		"external_canary_read": string(data) == marker,
		"src_with_restrictive_prefix_rejected": constrainedErr != nil,
		"file_id_traversal_rejected": traversal == nil,
	}
	encoded, err := json.MarshalIndent(result, "", "  ")
	if err != nil { panic(err) }
	fmt.Println(string(encoded))
}
