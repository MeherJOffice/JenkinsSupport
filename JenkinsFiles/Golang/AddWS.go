package main

import (
    "encoding/xml"
    "fmt"
    "io/ioutil"
    "os"
    "path/filepath"
    "strings"
)

type FileRef struct {
    XMLName  xml.Name `xml:"FileRef"`
    Location string   `xml:"location,attr"`
}
type Workspace struct {
    XMLName  xml.Name  `xml:"Workspace"`
    Version  string    `xml:"version,attr"`
    FileRefs []FileRef `xml:"FileRef"`
}

func main() {
    baseDir := "/Users/meher/jenkinsBuild/Fact or Fib! copy"

    // Find the .xcodeproj directly in projDir (not recursive)
    projDir := filepath.Join(baseDir, "CocosProject/build/ios/proj")
    entries, err := ioutil.ReadDir(projDir)
    if err != nil {
        fmt.Printf("❌ Failed to read dir %s: %v\n", projDir, err)
        os.Exit(1)
    }
    xcodeProjPath := ""
    for _, entry := range entries {
        if entry.IsDir() && strings.HasSuffix(entry.Name(), ".xcodeproj") {
            xcodeProjPath = filepath.Join(projDir, entry.Name())
            break
        }
    }
    if xcodeProjPath == "" {
        fmt.Println("❌ No .xcodeproj found directly in", projDir)
        os.Exit(1)
    }
    fmt.Println("✅ Found Xcode project:", xcodeProjPath)

    // Find the only .xcworkspace file under XcodeWorkspace (recursive is fine)
    wsDir := filepath.Join(baseDir, "XcodeWorkspace")
    var workspaceFile string
    filepath.Walk(wsDir, func(path string, info os.FileInfo, err error) error {
        if err == nil && strings.HasSuffix(info.Name(), ".xcworkspace") {
            workspaceFile = filepath.Join(path, "contents.xcworkspacedata")
            return filepath.SkipDir
        }
        return nil
    })
    if workspaceFile == "" {
        fmt.Println("❌ No .xcworkspace file found.")
        os.Exit(1)
    }
    fmt.Println("✅ Found workspace file:", workspaceFile)

    // Load and parse workspace XML
    data, err := ioutil.ReadFile(workspaceFile)
    if err != nil {
        fmt.Println("❌ Failed to read workspace file:", err)
        os.Exit(1)
    }
    var ws Workspace
    if err := xml.Unmarshal(data, &ws); err != nil {
        fmt.Println("❌ Failed to parse workspace XML:", err)
        os.Exit(1)
    }

    // Use the absolute path
    absXcodeProjPath, err := filepath.Abs(xcodeProjPath)
    if err != nil {
        fmt.Println("❌ Failed to get absolute path:", err)
        os.Exit(1)
    }
    locationStr := "absolute:" + absXcodeProjPath

    // Check if already present
    for _, fr := range ws.FileRefs {
        if fr.Location == locationStr {
            fmt.Println("ℹ️ Xcode project already present in workspace.")
            os.Exit(0)
        }
    }
    // Add new FileRef
    ws.FileRefs = append(ws.FileRefs, FileRef{Location: locationStr})

    // Marshal and save back
    out, _ := xml.MarshalIndent(ws, "", "   ")
    out = []byte(xml.Header + string(out))
    err = ioutil.WriteFile(workspaceFile, out, 0644)
    if err != nil {
        fmt.Println("❌ Failed to write workspace file:", err)
        os.Exit(1)
    }
    fmt.Println("✅ Xcode project added to workspace with absolute path.")
}
