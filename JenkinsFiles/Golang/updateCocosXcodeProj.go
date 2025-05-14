package main

import (
	"crypto/rand"
	"encoding/hex"
	"fmt"
	"log"
	"os"
	"path/filepath"
	"strings"

	"howett.net/plist"
)

func main() {
	logFile, _ := os.Create("/tmp/cocos_xcode_patch.log")
	log.SetOutput(logFile)
	defer logFile.Close()

	cwd, _ := os.Getwd()
	log.Println("📁 Working directory:", cwd)

	// Define project paths
	cocosProj := filepath.Join(cwd, "CocosBuild/jsb-default/frameworks/runtime-src/proj.ios_mac")
	unityProj := filepath.Join(cwd, "UnityBuild")

	cocosPbxprojPath := filepath.Join(cocosProj, "FactorFib.xcodeproj/project.pbxproj")
	unityPbxprojPath := filepath.Join(unityProj, "Unity-iPhone.xcodeproj/project.pbxproj")

	// Load both Xcode projects
	cocosProjMap := loadPbxproj(cocosPbxprojPath)
	unityProjMap := loadPbxproj(unityPbxprojPath)

	cocosObjects := cocosProjMap["objects"].(map[string]interface{})
	unityObjects := unityProjMap["objects"].(map[string]interface{})

	// Step 1: Find UnityFramework.framework fileRef from Unity project
	unityFrameworkRef := findUnityFrameworkFileRef(unityObjects)
	if unityFrameworkRef == "" {
		log.Fatal("❌ UnityFramework.framework not found in Unity project")
	}
	log.Println("📦 Found UnityFramework.framework fileRef:", unityFrameworkRef)

	// Step 2: Add fileRef to Cocos if not exists
	cocosFrameworkRef := ensureFileReferenceExists(cocosObjects, "UnityFramework.framework", "SOURCE_ROOT", "wrapper.framework")
	log.Println("📎 Reusing or created fileRef put in Cocos:", cocosFrameworkRef)

	// Step 3: Find first native target
	targetID, targetName := findFirstNativeTarget(cocosObjects)
if targetID == "" {
	log.Fatal("❌ Could not find suitable native target (non-desktop)")
}
log.Println("🎯 Using target:", targetName)

	// Step 4: Add to Embed Frameworks phase
	embedPhaseID := findOrCreateEmbedFrameworksPhase(cocosObjects, targetID)
	addToBuildPhase(cocosObjects, embedPhaseID, cocosFrameworkRef, true)

	// Step 5: Remove from Link Binary With Libraries
	removeFromBuildPhase(cocosObjects, targetID, cocosFrameworkRef, "PBXFrameworksBuildPhase")

	savePbxproj(cocosPbxprojPath, cocosProjMap)

	log.Println("🎉 Cocos Xcode project patched successfully.")
	fmt.Println("🎉 Cocos Xcode project patched successfully.")
}

func loadPbxproj(path string) map[string]interface{} {
	data, err := os.ReadFile(path)
	if err != nil {
		log.Fatalf("❌ Failed to read pbxproj: %v", err)
	}
	var result map[string]interface{}
	if _, err := plist.Unmarshal(data, &result); err != nil {
		log.Fatalf("❌ Failed to parse pbxproj: %v", err)
	}
	return result
}

func findUnityFrameworkFileRef(objects map[string]interface{}) string {
	for id, obj := range objects {
		if m, ok := obj.(map[string]interface{}); ok &&
			m["isa"] == "PBXFileReference" &&
			m["path"] == "UnityFramework.framework" {
			return id
		}
	}
	return ""
}

func ensureFileReferenceExists(objects map[string]interface{}, path, sourceTree, fileType string) string {
	for id, obj := range objects {
		if m, ok := obj.(map[string]interface{}); ok &&
			m["isa"] == "PBXFileReference" &&
			m["path"] == path {
			return id
		}
	}
	id := generateUUID()
	objects[id] = map[string]interface{}{
		"isa":             "PBXFileReference",
		"path":            path,
		"sourceTree":      sourceTree,
		"explicitFileType": fileType,
	}
	return id
}

func findFirstNativeTarget(objects map[string]interface{}) (string, string) {
	for id, obj := range objects {
		if m, ok := obj.(map[string]interface{}); ok &&
			m["isa"] == "PBXNativeTarget" {
			name, _ := m["name"].(string)
			// Skip desktop, test, or mac-related targets
			lower := strings.ToLower(name)
			if strings.Contains(lower, "desktop") || strings.Contains(lower, "test") || strings.Contains(lower, "mac") {
				continue
			}
			return id, name
		}
	}
	return "", ""
}

func findOrCreateEmbedFrameworksPhase(objects map[string]interface{}, targetID string) string {
	target := objects[targetID].(map[string]interface{})
	buildPhases := target["buildPhases"].([]interface{})
	for _, id := range buildPhases {
		phase := objects[id.(string)].(map[string]interface{})
		if phase["isa"] == "PBXCopyFilesBuildPhase" &&
			phase["dstSubfolderSpec"] == 10.0 {
			return id.(string)
		}
	}
	// Create new embed frameworks phase
	id := generateUUID()
	objects[id] = map[string]interface{}{
		"isa":              "PBXCopyFilesBuildPhase",
		"buildActionMask":  2147483647,
		"dstPath":          "",
		"dstSubfolderSpec": 10.0,
		"files":            []interface{}{},
		"name":             "Embed Frameworks",
		"runOnlyForDeploymentPostprocessing": 0,
	}
	target["buildPhases"] = append(buildPhases, id)
	return id
}

func addToBuildPhase(objects map[string]interface{}, phaseID, fileRefID string, embed bool) {
	phase := objects[phaseID].(map[string]interface{})
	files := phase["files"].([]interface{})
	for _, id := range files {
		build := objects[id.(string)].(map[string]interface{})
		if build["fileRef"] == fileRefID {
			log.Println("ℹ️ UnityFramework.framework already added to phase")
			return
		}
	}
	buildFileID := generateUUID()
	settings := map[string]interface{}{}
	if embed {
		settings["ATTRIBUTES"] = []interface{}{"CodeSignOnCopy", "RemoveHeadersOnCopy"}
	}
	objects[buildFileID] = map[string]interface{}{
		"isa":      "PBXBuildFile",
		"fileRef":  fileRefID,
		"settings": settings,
	}
	phase["files"] = append(files, buildFileID)
	log.Println("✅ Added UnityFramework.framework to Embed Frameworks")
}

func removeFromBuildPhase(objects map[string]interface{}, targetID, fileRefID, isa string) {
	target := objects[targetID].(map[string]interface{})
	for _, phaseID := range target["buildPhases"].([]interface{}) {
		phase := objects[phaseID.(string)].(map[string]interface{})
		if phase["isa"] != isa {
			continue
		}
		newFiles := []interface{}{}
		for _, fileID := range phase["files"].([]interface{}) {
			buildFile := objects[fileID.(string)].(map[string]interface{})
			if buildFile["fileRef"] == fileRefID {
				log.Println("🗑 Removed UnityFramework.framework from", isa, ":", fileID)
				delete(objects, fileID.(string))
				continue
			}
			newFiles = append(newFiles, fileID)
		}
		phase["files"] = newFiles
	}
}

func savePbxproj(path string, project map[string]interface{}) {
	f, err := os.Create(path)
	if err != nil {
		log.Fatal("❌ Failed to open pbxproj for writing:", err)
	}
	defer f.Close()
	if err := plist.NewEncoderForFormat(f, plist.XMLFormat).Encode(project); err != nil {
		log.Fatal("❌ Failed to write pbxproj:", err)
	}
}

func generateUUID() string {
	b := make([]byte, 12)
	_, _ = rand.Read(b)
	return strings.ToUpper(hex.EncodeToString(b))
}
