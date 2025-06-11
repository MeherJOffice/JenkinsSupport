package main

import (
    "encoding/xml"
    "fmt"
    "io/ioutil"
    "os"
    "os/exec"
    "path/filepath"
    "strings"
    "time"
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
    // Auto-detect working directory (binary, cocosProject, workspace must be together)
    exePath, _ := os.Executable()
    baseDir := filepath.Dir(exePath)
    cocosProject := filepath.Join(baseDir, "cocosProject")
    creatorPath := "/Applications/CocosCreator.app/Contents/MacOS/CocosCreator"
    configPath := filepath.Join(cocosProject, "buildConfig_ios.json")

    // --- Step 1: Clean up folders ---
    for _, folder := range []string{"build", "temp", "library"} {
        fullPath := filepath.Join(cocosProject, folder)
        fmt.Printf("🧹 Removing folder: %s\n", fullPath)
        os.RemoveAll(fullPath)
    }
    fmt.Println("✅ Cleaned build, temp, and library folders.")

    // --- Step 2: Wait for 2 seconds ---
    fmt.Println("⏳ Waiting 2 seconds for stabilization...")
    time.Sleep(2 * time.Second)

    // --- Step 3: Build cocos project, capture log ---
    logFile := filepath.Join(baseDir, "cocos_build.log")
    buildCmd := exec.Command(
        creatorPath,
        "--project", cocosProject,
        "--build", fmt.Sprintf("platform=ios;debug=false;configPath=%s", configPath),
    )
    logF, _ := os.Create(logFile)
    defer logF.Close()
    buildCmd.Stdout = logF
    buildCmd.Stderr = logF

    fmt.Println("🚀 Building Cocos project...")
    err := buildCmd.Run()
    logF.Sync()

    // --- Step 4: Check build log for success ---
    logBytes, _ := ioutil.ReadFile(logFile)
    logText := string(logBytes)
    if strings.Contains(logText, "build success") {
        fmt.Println("✅ Cocos project build finished (build success detected).")
    } else {
        fmt.Println("❌ Cocos build failed.")
        fmt.Println(logText)
        if err != nil {
            fmt.Printf("Error: %v\n", err)
        }
        os.Exit(1)
    }

    // --- Step 5: Find .xcodeproj and add to workspace ---
    // Find the .xcodeproj directly in build/ios/proj (not recursive)
    projDir := filepath.Join(cocosProject, "build/ios/proj")
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

    // Find the only .xcworkspace file under XcodeWorkspace
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
