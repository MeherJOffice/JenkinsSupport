package main

import (
    "fmt"
    "os"
    "os/exec"
    "path/filepath"
    "time"
    "io/ioutil"
    "strings"
)

func main() {
    exePath, _ := os.Executable()
    baseDir := filepath.Dir(exePath)
    cocosProject := filepath.Join(baseDir, "cocosProject")
    creatorPath := "/Applications/CocosCreator.app/Contents/MacOS/CocosCreator"
    configPath := filepath.Join(cocosProject, "buildConfig_ios.json")

    // Clean up folders
    for _, folder := range []string{"build", "temp", "library"} {
        fullPath := filepath.Join(cocosProject, folder)
        fmt.Printf("🧹 Removing folder: %s\n", fullPath)
        os.RemoveAll(fullPath)
    }
    fmt.Println("✅ Cleaned build, temp, and library folders.")

    fmt.Println("⏳ Waiting 2 seconds for stabilization...")
    time.Sleep(2 * time.Second)

    // Prepare log file path
    logFile := filepath.Join(baseDir, "cocos_build.log")
    buildCmd := exec.Command(
        creatorPath,
        "--project", cocosProject,
        "--build", fmt.Sprintf("platform=ios;debug=false;configPath=%s", configPath),
    )

    // Redirect output to log file
    logF, _ := os.Create(logFile)
    defer logF.Close()
    buildCmd.Stdout = logF
    buildCmd.Stderr = logF

    fmt.Println("🚀 Building Cocos project...")
    err := buildCmd.Run()
    logF.Sync()

    // Read log for "build success"
    logBytes, _ := ioutil.ReadFile(logFile)
    logText := string(logBytes)
    if strings.Contains(logText, "build success") {
        fmt.Println("✅ Cocos project build finished (build success detected).")
        os.Exit(0)
    }

    // If not successful
    fmt.Println("❌ Cocos build failed.")
    fmt.Println(logText)
    if err != nil {
        fmt.Printf("Error: %v\n", err)
    }
    os.Exit(1)
}
