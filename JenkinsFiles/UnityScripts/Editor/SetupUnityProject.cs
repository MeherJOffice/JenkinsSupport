using UnityEditor;
using UnityEngine;
using UnityEditor.SceneManagement;
using System.Linq;
using System.IO;

public static class SetupUnityProject
{
    [MenuItem("Tools/SetupUnityProject")]
    public static void SetupProjectForSDK()
    {
        AddDefineSymbolForIOS("SDK");
        AddGameObject();
    }

    public static void AddDefineSymbolForIOS(string symbol)
    {
        var buildTargetGroup = BuildTargetGroup.iOS;
        var defines = PlayerSettings.GetScriptingDefineSymbolsForGroup(buildTargetGroup);

        if (!defines.Split(';').Contains(symbol))
        {
            var newDefines = string.IsNullOrEmpty(defines) ? symbol : $"{defines};{symbol}";
            PlayerSettings.SetScriptingDefineSymbolsForGroup(buildTargetGroup, newDefines);
            Debug.Log($"✅ Added scripting define symbol '{symbol}' for iOS.");
        }
        else
        {
            Debug.Log($"ℹ️ Define symbol '{symbol}' already present for iOS.");
        }
    }
    public static void AddGameObject()
    {
        string scriptPathEnv = System.Environment.GetEnvironmentVariable("SCRIPT_TO_PATCH");
        string sceneIndexEnv = System.Environment.GetEnvironmentVariable("SCENE_INDEX_TO_PATCH");

        if (string.IsNullOrEmpty(scriptPathEnv))
        {
            Debug.LogError("❌ SCRIPT_TO_PATCH environment variable is not set.");
            return;
        }

        if (string.IsNullOrEmpty(sceneIndexEnv) || !int.TryParse(sceneIndexEnv, out int sceneIndex))
        {
            Debug.LogError("❌ SCENE_INDEX_TO_PATCH is not set or not a valid integer.");
            return;
        }

        string scriptFileName = Path.GetFileNameWithoutExtension(scriptPathEnv);
        Debug.Log($"Detected script name to attach: {scriptFileName}");
        Debug.Log($"Using scene index: {sceneIndex}");

        var enabledScenes = EditorBuildSettings.scenes.Where(s => s.enabled).ToArray();

        if (sceneIndex < 0 || sceneIndex >= enabledScenes.Length)
        {
            Debug.LogError($"❌ Scene index {sceneIndex} is out of range. Found {enabledScenes.Length} enabled scenes.");
            return;
        }

        var targetScenePath = enabledScenes[sceneIndex].path;
        Debug.Log($"✅ Target scene: {targetScenePath}");

        var scene = EditorSceneManager.OpenScene(targetScenePath);

        var newObj = new GameObject("InjectedObject");

        var scriptType = System.AppDomain.CurrentDomain
            .GetAssemblies()
            .SelectMany(a => a.GetTypes())
            .FirstOrDefault(t => t.Name == scriptFileName);

        if (scriptType == null)
        {
            Debug.LogError($"❌ Could not find a script type with the name: {scriptFileName}");
            return;
        }

        newObj.AddComponent(scriptType);
        Debug.Log($"✅ InjectedObject created and script '{scriptFileName}' attached.");

        EditorSceneManager.SaveScene(scene);
        Debug.Log("✅ Scene saved successfully.");
    }
}
