package main

import (
    "fmt"
    "os"
    "path/filepath"
    "io"
)

func main() {
    exePath, _ := os.Executable()
    baseDir := filepath.Dir(exePath)

    unityIcons := filepath.Join(baseDir, "UnityBuild/Unity-iPhone/Images.xcassets/AppIcon.appiconset")
    cocosIcons := filepath.Join(baseDir, "cocosProject/native/engine/ios/Images.xcassets/AppIcon.appiconset")

    // Ensure source exists
    srcInfo, err := os.Stat(unityIcons)
    if err != nil || !srcInfo.IsDir() {
        fmt.Printf("❌ Unity AppIcon.appiconset not found at %s\n", unityIcons)
        os.Exit(1)
    }

    // Delete the target folder if it exists
    os.RemoveAll(cocosIcons)

    // Copy the entire folder (including files)
    err = copyDir(unityIcons, cocosIcons)
    if err != nil {
        fmt.Printf("❌ Failed to copy icon set folder: %v\n", err)
        os.Exit(1)
    }
    fmt.Println("✅ Entire Unity AppIcon.appiconset replaced Cocos icon set.")
}

func copyDir(src string, dst string) error {
    entries, err := os.ReadDir(src)
    if err != nil {
        return err
    }
    if err := os.MkdirAll(dst, 0755); err != nil {
        return err
    }
    for _, entry := range entries {
        srcPath := filepath.Join(src, entry.Name())
        dstPath := filepath.Join(dst, entry.Name())

        if entry.IsDir() {
            if err := copyDir(srcPath, dstPath); err != nil {
                return err
            }
        } else {
            if err := copyFile(srcPath, dstPath); err != nil {
                return err
            }
        }
    }
    return nil
}

func copyFile(src, dst string) error {
    in, err := os.Open(src)
    if err != nil {
        return err
    }
    defer in.Close()

    out, err := os.Create(dst)
    if err != nil {
        return err
    }
    defer out.Close()

    _, err = io.Copy(out, in)
    if err != nil {
        return err
    }

    return out.Sync()
}
