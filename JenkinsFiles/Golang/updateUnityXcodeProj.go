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
	f, err := os.Create("/tmp/unity_xcode_patch.log")
	if err != nil {
		log.Fatal("❌ Failed to create log file:", err)
	}
	defer f.Close()
	log.SetOutput(f)

	cwd, _ := os.Getwd()
	log.Println("📁 Working directory:", cwd)

	pbxprojPath := filepath.Join(cwd, "UnityBuild", "Unity-iPhone.xcodeproj", "project.pbxproj")
	dataFolder := "Data"
	targetName := "UnityFramework"

	raw, err := os.ReadFile(pbxprojPath)
	if err != nil {
		log.Fatal("❌ Failed to read pbxproj:", err)
	}

	var project map[string]interface{}
	if _, err := plist.Unmarshal(raw, &project); err != nil {
		log.Fatal("❌ Failed to parse pbxproj:", err)
	}

	objects := project["objects"].(map[string]interface{})

	targetID := findTargetID(objects, targetName)
	if targetID == "" {
		log.Fatal("❌ UnityFramework target not found")
	}
	log.Println("🎯 Found UnityFramework target:", targetID)

	dataFileRefID := ensureDataFileReference(objects, dataFolder)
	removeDataFromTarget(objects, "Unity-iPhone", dataFileRefID)
	addDataToTarget(objects, targetID, dataFileRefID)

	if err := updateHeaderVisibility(objects); err != nil {
		log.Fatal("❌ Header visibility update failed:", err)
	}

	uiFilePath := filepath.Join(cwd, "UnityBuild", "Classes", "UI", "UnityViewControllerBase+iOS.mm")
	if err := patchShouldAutorotate(uiFilePath); err != nil {
		log.Fatal(err)
	}

	// ✅ Overwrite PrivacyInfo.xcprivacy from local working dir to UnityFramework folder
	err = overwritePrivacyInfo(cwd)
	if err != nil {
		log.Fatal("❌ Failed to overwrite PrivacyInfo.xcprivacy:", err)
	}

	if err := savePbxproj(pbxprojPath, project); err != nil {
		log.Fatal("❌ Failed to save project:", err)
	}

	log.Println("🎉 Unity Xcode project patched successfully.")
	fmt.Println("🎉 Unity Xcode project patched successfully.")
}

func overwritePrivacyInfo(cwd string) error {
	src := filepath.Join(cwd, "PrivacyInfo.xcprivacy")
	dst := filepath.Join(cwd, "UnityBuild", "UnityFramework", "PrivacyInfo.xcprivacy")

	log.Println("📂 Overwriting PrivacyInfo.xcprivacy")
	log.Println("📄 Source:", src)
	log.Println("📄 Destination:", dst)

	data, err := os.ReadFile(src)
	if err != nil {
		return fmt.Errorf("❌ Failed to read override file: %w", err)
	}

	err = os.WriteFile(dst, data, 0644)
	if err != nil {
		return fmt.Errorf("❌ Failed to write to destination: %w", err)
	}
	log.Println("✅ Overwrote PrivacyInfo.xcprivacy successfully")
	return nil
}

// --- Existing helper functions follow ---

func findTargetID(objects map[string]interface{}, name string) string {
	for id, obj := range objects {
		if m, ok := obj.(map[string]interface{}); ok && m["isa"] == "PBXNativeTarget" && m["name"] == name {
			return id
		}
	}
	return ""
}

func ensureDataFileReference(objects map[string]interface{}, path string) string {
	for id, obj := range objects {
		if m, ok := obj.(map[string]interface{}); ok &&
			m["isa"] == "PBXFileReference" &&
			m["path"] == path {
			log.Println("📁 Found existing file reference for Data:", id)
			return id
		}
	}

	id := generateUUID()
	objects[id] = map[string]interface{}{
		"isa":              "PBXFileReference",
		"path":             path,
		"name":             "Data",
		"sourceTree":       "SOURCE_ROOT",
		"explicitFileType": "folder",
	}
	log.Println("📁 Created new file reference for Data:", id)
	return id
}

func removeDataFromTarget(objects map[string]interface{}, targetName, dataRefID string) {
	targetID := findTargetID(objects, targetName)
	if targetID == "" {
		log.Println("ℹ️ No target found named:", targetName)
		return
	}
	target := objects[targetID].(map[string]interface{})
	for _, phaseID := range target["buildPhases"].([]interface{}) {
		phase := objects[phaseID.(string)].(map[string]interface{})
		if phase["isa"] == "PBXResourcesBuildPhase" {
			newFiles := []interface{}{}
			for _, fileID := range phase["files"].([]interface{}) {
				buildFile := objects[fileID.(string)].(map[string]interface{})
				if buildFile["fileRef"] == dataRefID {
					log.Println("🗑 Removing Data from Unity-iPhone:", fileID)
					delete(objects, fileID.(string))
					continue
				}
				newFiles = append(newFiles, fileID)
			}
			phase["files"] = newFiles
		}
	}
}

func addDataToTarget(objects map[string]interface{}, targetID, dataRefID string) {
	target := objects[targetID].(map[string]interface{})
	var resourcesPhaseID string
	for _, phaseID := range target["buildPhases"].([]interface{}) {
		phase := objects[phaseID.(string)].(map[string]interface{})
		if phase["isa"] == "PBXResourcesBuildPhase" {
			resourcesPhaseID = phaseID.(string)
			break
		}
	}
	if resourcesPhaseID == "" {
		log.Fatal("❌ No PBXResourcesBuildPhase found for target:", targetID)
	}

	phase := objects[resourcesPhaseID].(map[string]interface{})
	files := phase["files"].([]interface{})
	for _, fileID := range files {
		buildFile := objects[fileID.(string)].(map[string]interface{})
		if buildFile["fileRef"] == dataRefID {
			log.Println("ℹ️ Data already present in UnityFramework")
			return
		}
	}

	buildFileID := generateUUID()
	objects[buildFileID] = map[string]interface{}{
		"isa":     "PBXBuildFile",
		"fileRef": dataRefID,
	}
	files = append(files, buildFileID)
	phase["files"] = files
	log.Println("✅ Added Data to UnityFramework build phase")
}

func updateHeaderVisibility(objects map[string]interface{}) error {
	log.Println("🔍 Searching for .h file under Libraries/Plugins/iOS")
	for id, obj := range objects {
		m, ok := obj.(map[string]interface{})
		if !ok || m["isa"] != "PBXFileReference" {
			continue
		}
		path, ok := m["path"].(string)
		if !ok || !strings.HasSuffix(path, ".h") || !strings.Contains(path, "Libraries/Plugins/iOS") {
			continue
		}
		log.Println("📄 Found .h file:", path, "ID:", id)
		// Now find build file referencing this
		for buildID, buildObj := range objects {
			b, ok := buildObj.(map[string]interface{})
			if !ok || b["isa"] != "PBXBuildFile" {
				continue
			}
			if b["fileRef"] == id {
				log.Println("🛠 Updating build file to public visibility:", buildID)
				b["settings"] = map[string]interface{}{
					"ATTRIBUTES": []string{"Public"},
				}
				return nil
			}
		}
		return fmt.Errorf("❌ Build file not found for header: %s", path)
	}
	return fmt.Errorf("❌ No .h file found in Plugins/IOS")
}

func patchShouldAutorotate(filePath string) error {
	log.Println("🛠 Patching shouldAutorotate in:", filePath)

	data, err := os.ReadFile(filePath)
	if err != nil {
		return fmt.Errorf("❌ Failed to read file: %w", err)
	}

	content := string(data)
	start := strings.Index(content, "- (BOOL)shouldAutorotate")
	if start == -1 {
		return fmt.Errorf("❌ Method not found: - (BOOL)shouldAutorotate")
	}

	end := strings.Index(content[start:], "}")
	if end == -1 {
		return fmt.Errorf("❌ Could not find method end bracket")
	}

	block := content[start : start+end]
	if !strings.Contains(block, "return YES;") {
		return fmt.Errorf("❌ return YES; not found inside shouldAutorotate")
	}

	updatedBlock := strings.Replace(block, "return YES;", "return NO;", 1)
	content = content[:start] + updatedBlock + content[start+end:]

	if err := os.WriteFile(filePath, []byte(content), 0644); err != nil {
		return fmt.Errorf("❌ Failed to write back patched file: %w", err)
	}

	log.Println("✅ Patched shouldAutorotate successfully")
	return nil
}

func savePbxproj(path string, project map[string]interface{}) error {
	f, err := os.Create(path)
	if err != nil {
		return err
	}
	defer f.Close()
	return plist.NewEncoderForFormat(f, plist.XMLFormat).Encode(project)
}

func generateUUID() string {
	b := make([]byte, 12)
	rand.Read(b)
	return strings.ToUpper(hex.EncodeToString(b))
}
