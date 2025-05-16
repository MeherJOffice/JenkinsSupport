pipeline {
    agent any

    parameters {
        string(name: 'UNITY_PROJECT_PATH', defaultValue: '/Users/meher/Documents/GitHub/Games-Meher/Fact Or Lie', description: 'Local path to Unity project')
        string(name: 'PLUGINS_PROJECT_PATH', defaultValue: '/Users/meher/Documents/GitHub/UpStoreTools', description: 'Local path to Plugins repo')
        string(name: 'COCOS_PROJECT_PATH', defaultValue: '/Users/meher/Documents/GitHub/SDKForJenkins/JenkensCocos2unity', description: 'Cocos project path')
        text(name: 'UNITY_OVERRIDE_VALUE' , description: 'Override value for Unity')
        string(name: 'COCOS_OVERRIDE_VALUE' , description: 'Override value for Cocos')
        choice(name: 'COCOS_VERSION', choices: ['cocos2', 'cocos3'], description: 'Cocos version')
        choice(name: 'GAME_ENGINE', choices: ['unity', 'cocos2', 'cocos3'], description: 'Game engine to build')
        string(name: 'SCENE_INDEX_TO_PATCH', defaultValue: '0', description: 'Index of the scene in Build Settings to inject the object into')
        booleanParam(name: 'TESTING', defaultValue: false, description: 'If true, use last month’s date; if false, use today’s date')
    }
    environment {
        PATH = "/usr/local/go/bin:${env.PATH}" // Adjust path if needed
        HOME_DIR = "${env.HOME}"
    }
    stages {
        stage('Reset Plugin Repo') {
            steps {
                script {
                    def pluginPath = params.PLUGINS_PROJECT_PATH

                    echo "🧹 Cleaning Git repo at: ${pluginPath}"

                    sh """
                cd '${pluginPath}'
                git reset --hard HEAD
                git clean -fd
            """

                    echo '✅ Plugin repo reset to a clean state.'
                }
            }
        }

        stage('Validate Paths') {
            steps {
                echo "Unity Project Path: ${params.UNITY_PROJECT_PATH}"
                echo "Plugins Repo Path: ${params.PLUGINS_PROJECT_PATH}"
                echo "Selected Engine: ${params.GAME_ENGINE}"
                echo "Selected Cocos Version: ${params.COCOS_VERSION}"

                script {
                    if (!fileExists("${params.UNITY_PROJECT_PATH}")) {
                        error("Unity project path does not exist: ${params.UNITY_PROJECT_PATH}")
                    }
                    if (!fileExists("${params.PLUGINS_PROJECT_PATH}")) {
                        error("Plugins repo path does not exist: ${params.PLUGINS_PROJECT_PATH}")
                    }
                }
            }
        }

        stage('Preprocess FE2In.cs (Unity Script)') {
            when {
                expression { params.GAME_ENGINE == 'unity' }
            }
            steps {
                script {
                    echo '🧠 Preprocessing FE2In.cs...'

                    def venvPath = sh(
                script: "find $HOME/.venvs -name 'pbxproj-env' -type d | head -n 1",
                returnStdout: true
            ).trim()

                    if (!venvPath) {
                        error '❌ Virtual environment not found!'
                    }
                    echo "✅ Found VENV at: ${venvPath}"

                    def fe2inScript = "${params.PLUGINS_PROJECT_PATH}/unityProj/Assets/Scripts/FE2In.cs"
                    def fe2inPy = "${fe2inScript}.py"
                    def jenkinsfiles = "${env.WORKSPACE}/JenkinsFiles"

                    // Copy the Python script next to the C# script
                    sh "cp '${jenkinsfiles}/Python/PreprocessFE2In.py' '${fe2inPy}'"

                    // Run the Python script
                    sh """
                source '${venvPath}/bin/activate' && \
                python3 '${fe2inPy}' '${fe2inScript}' '${params.UNITY_OVERRIDE_VALUE}' '${params.TESTING}'
            """

                    // Run Unity script modifier
                    sh "chmod +x '${params.PLUGINS_PROJECT_PATH}/shuffleAndRandomizeCode'"
                    sh "'${params.PLUGINS_PROJECT_PATH}/shuffleAndRandomizeCode'"

                    // 🔥 Delete the temporary Python script
                    sh "rm -f '${fe2inPy}'"

                    echo '✅ FE2In.cs processed and temporary files cleaned.'
                }
            }
        }

stage('Preprocess CheckStatus.ts (Before Copy)') {
    steps {
        script {
            echo '⚙️ Preprocessing CheckStatus.ts with override and date...'

            def venvPath = sh(
                script: "find $HOME/.venvs -name 'pbxproj-env' -type d | head -n 1",
                returnStdout: true
            ).trim()

            if (!venvPath) {
                error '❌ Python virtual environment not found!'
            }

            def tsFilePath = "${params.PLUGINS_PROJECT_PATH}/BootUnity213/assets/LoadScene/CheckStatus.ts"
            def override = params.COCOS_OVERRIDE_VALUE
            def testingFlag = params.TESTING.toString().toLowerCase()
            def jenkinsfiles = "${env.WORKSPACE}/JenkinsFiles"

            // Run the Python preprocessor
            sh """
                source '${venvPath}/bin/activate' && \
                python3 '${jenkinsfiles}/Python/PreprocessCheckStatus.py' '${tsFilePath}' '${override}' '${testingFlag}'
            """

            // Run prepareUpStore and capture full output
            def prepareOutput = sh(
                script: "'${params.PLUGINS_PROJECT_PATH}/BootUnity213/prepareUpStore' 2>&1",
                returnStdout: true
            ).trim()

            echo "📋 prepareUpStore output:"
            prepareOutput.readLines().each { line -> echo "│ ${line}" }

            // Extract new filename using the full multiline string
            def newFileName = null
            def matcher = prepareOutput =~ /__updating ts file from: .*CheckStatus\.ts to .*\/([A-Za-z0-9_]+\.ts)/
            if (matcher.find()) {
                newFileName = matcher.group(1)
            } else {
                echo "❗ Could not match CheckStatus.ts rename. Full output:"
                prepareOutput.readLines().each { line -> echo "  >> ${line}" }
                error '❌ Failed to extract new filename for CheckStatus.ts!'
            }

            echo "✅ New CheckStatus.ts filename: ${newFileName}"

            // Construct JSON path
            def jsonFilePath = "${env.WORKSPACE}/jenkinsBuild/filenameMap.json"
            sh """
                mkdir -p '${env.WORKSPACE}/jenkinsBuild' && \
                echo '{' > '${jsonFilePath}' && \
                echo '  "CheckstatutName": "${newFileName}",' >> '${jsonFilePath}' && \
                echo '  "FEln Name": "${newFileName}"' >> '${jsonFilePath}' && \
                echo '}' >> '${jsonFilePath}'
            """

            echo "✅ Saved mapping to: ${jsonFilePath}"
        }
    }
}





        stage('Sync BootUnity213 for Unity + Cocos 2.1.3') {
            when {
                expression {
                    return params.GAME_ENGINE == 'unity' && params.COCOS_VERSION == 'cocos2'
                }
            }
            steps {
                script {
                    echo '🔄 Syncing BootUnity213 into Cocos project...'

                    def pluginRepo = "${params.PLUGINS_PROJECT_PATH}"
                    def cocosProjectPath = "${params.COCOS_PROJECT_PATH}"

                    // Paths to copy from
                    def bootUnityPath = "${pluginRepo}/BootUnity213"

                    // Items to copy
                    def foldersToCopy = ['assets', 'build-templates', 'settings']
                    def filesToCopy = ['changeLibCC', 'creator.d.ts', 'jsconfig.json', 'project.json']

                    // Build copy command
                    def copyCommands = []

                    foldersToCopy.each {
                folder->
                        copyCommands << "rm -rf '${cocosProjectPath}/${folder}'"
                        copyCommands << "cp -R '${bootUnityPath}/${folder}' '${cocosProjectPath}/'"
                    }

                    filesToCopy.each {
                file->
                        copyCommands << "rm -f '${cocosProjectPath}/${file}'"
                        copyCommands << "cp '${bootUnityPath}/${file}' '${cocosProjectPath}/'"
                    }

                    sh """
                set -e
                echo "📁 Plugin repo path: ${pluginRepo}"
                echo "🎮 Cocos project path: ${cocosProjectPath}"

                ${copyCommands.join('\n')}

                echo "✅ BootUnity213 synced successfully."
            """
                }
            }
        }

        stage('Build Cocos Project') {
                    when {
                        expression { params.GAME_ENGINE == 'unity' && params.COCOS_VERSION == 'cocos2' }
                    }
                    steps {
                        script {
                            echo '🚀 Preparing Cocos project build...'

                            // Define Cocos Creator executable path
                            def cocosCreatorPath = '/Applications/CocosCreator-2.1.3.app/Contents/MacOS/CocosCreator'

                            // Clean old build folder if it exists
                            def oldBuildPath = "${params.COCOS_PROJECT_PATH}/build"
                            echo "🧹 Checking and cleaning old build at: ${oldBuildPath}"

                            sh """
                if [ -d '${oldBuildPath}' ]; then
                    echo "🗑️ Old build found. Deleting..."
                    rm -rf '${oldBuildPath}'
                else
                    echo "✅ No old build to clean."
                fi
            """

                            // Start building
                            echo '🚀 Starting fresh Cocos project build...'
                            sh """
                '${cocosCreatorPath}' --path '${params.COCOS_PROJECT_PATH}' --build "platform=ios;debug=false"
            """

                            echo '✅ Cocos project build completed!'
                        }
                    }
        }

        stage('Update Cocos Build Settings') {
            when {
                expression { params.GAME_ENGINE == 'unity' && params.COCOS_VERSION == 'cocos2' }
            }
            steps {
                script {
                    echo '🔎 Searching for Python virtual environment...'

                    def venvPath = sh(
                script: "find $HOME/.venvs -name 'pbxproj-env' -type d | head -n 1",
                returnStdout: true
            ).trim()

                    if (!venvPath) {
                        error '❌ Virtual environment not found!'
                    }

                    echo "✅ Found VENV at: ${venvPath}"

                    // Extract product name
                    def productName = sh(
                script: "grep 'productName:' '${params.UNITY_PROJECT_PATH}/ProjectSettings/ProjectSettings.asset' | sed 's/^[^:]*: *//'",
                returnStdout: true
            ).trim()

                    // Try Unity 6+ format first
                    def bundleId = sh(
                script: """
                    awk '/applicationIdentifier:/,/^[^ ]/' '${params.UNITY_PROJECT_PATH}/ProjectSettings/ProjectSettings.asset' | \
                    grep 'iPhone:' | sed 's/^.*iPhone: *//' | head -n 1 | tr -d '\\n\\r'
                """,
                returnStdout: true
            ).trim()

                    // Fallback for older Unity versions
                    if (!bundleId) {
                        bundleId = sh(
                    script: """
                        grep 'bundleIdentifier:' '${params.UNITY_PROJECT_PATH}/ProjectSettings/ProjectSettings.asset' | \
                        sed 's/^[^:]*: *//' | head -n 1 | tr -d '\\n\\r'
                    """,
                    returnStdout: true
                ).trim()
                    }

                    echo "📦 Product Name: ${productName}"
                    echo "🔐 Bundle ID: ${bundleId}"
                    def jenkinsfiles = "${env.WORKSPACE}/JenkinsFiles"

                    def pythonFile = "${jenkinsfiles}/SetupCocosBuildSettings.py"
                    def cocosProject = params.COCOS_PROJECT_PATH

                    // Copy Python script into project
                    sh "cp '${jenkinsfiles}/Python/SetupCocosBuildSettings.py' '${pythonFile}'"

                    // Execute script
                    sh """
                source '${venvPath}/bin/activate' && \
                python3 '${pythonFile}' '${cocosProject}' '${bundleId}' '${productName}'
            """

                    // 🔥 Cleanup after execution
                    sh "rm -f '${pythonFile}'"
                    echo '🧹 Cleanup: Deleted SetupCocosBuildSettings.py'

                    echo '✅ Cocos build settings updated successfully.'
                }
            }
        }

stage('Copy Plugin Files to Unity Project') {
    when {
        expression { params.GAME_ENGINE == 'unity' }
    }
    steps {
        script {
            echo '🔄 Copying plugin folders (Editor, Plugins, Scripts) to Unity project, excluding .meta files...'

            def foldersToCopy = ['Editor', 'Plugins', 'Scripts']
            def copiedScriptName = ''
            def unityScriptPath = ''

            foldersToCopy.each { folder ->
                def sourcePath = "${params.PLUGINS_PROJECT_PATH}/unityProj/Assets/${folder}"
                def targetPath = "${params.UNITY_PROJECT_PATH}/Assets/${folder}"

                echo "📁 Preparing to copy: ${sourcePath} → ${targetPath}"
                sh "mkdir -p '${targetPath}'"

                // Copy all files recursively excluding .meta
                sh """
                    rsync -av --exclude='*.meta' '${sourcePath}/' '${targetPath}/'
                """

                echo "✅ Copied ${folder} folder successfully."

                // If it's the Scripts folder, detect the .cs file BEFORE copying
                if (folder == 'Scripts') {
                    def pluginScriptsPath = "${params.PLUGINS_PROJECT_PATH}/unityProj/Assets/Scripts"
                    def detectedCs = sh(
                        script: "find '${pluginScriptsPath}' -name '*.cs' | head -n 1",
                        returnStdout: true
                    ).trim()

                    if (!detectedCs) {
                        error "❌ No .cs script found in plugin Scripts folder!"
                    }

                    copiedScriptName = detectedCs.tokenize('/').last()
                    unityScriptPath = "${params.UNITY_PROJECT_PATH}/Assets/Scripts/${copiedScriptName}"
                }
            }

            // ✅ Set SCRIPT_TO_PATCH based on detected file
            if (!unityScriptPath) {
                error "❌ Could not determine SCRIPT_TO_PATCH!"
            }

            def editorfiles = "${env.WORKSPACE}/JenkinsFiles/UnityScripts/Editor"

            def editorTarget = "${params.UNITY_PROJECT_PATH}/Assets/Editor"

            echo "📂 Copying Editor scripts from ${editorfiles} to ${editorTarget}"
            sh """
                mkdir -p '${editorTarget}'
                rsync -av --exclude='*.meta' '${editorfiles}/' '${editorTarget}/'
            """
            
            env.SCRIPT_TO_PATCH = unityScriptPath
            echo "📌 SCRIPT_TO_PATCH set to: ${env.SCRIPT_TO_PATCH}"
            echo '🎉 All plugin folders copied successfully and SCRIPT_TO_PATCH is set.'
        }
    }
}



        stage('Add SharpZipLib Package via Package Manager') {
            when {
                expression { params.GAME_ENGINE == 'unity' }
            }
            steps {
                script {
                    def manifestPath = "${params.UNITY_PROJECT_PATH}/Packages/manifest.json"
                    def packageName = 'com.unity.sharp-zip-lib'
                    def packageVersion = '1.3.9'

                    echo 'Checking Unity manifest.json for SharpZipLib package...'

                    // Read manifest.json content
                    def manifestContent = readFile(manifestPath)
                    if (manifestContent.contains(packageName)) {
                        echo '✅ SharpZipLib already present in manifest.json.'
                    }
            else
            {
                        echo '🔧 Adding SharpZipLib to manifest.json...'

                        // Use sed to insert the package
                        sh """
                tmpfile=\$(mktemp)
                jq '.dependencies += {\"${packageName}\": \"${packageVersion}\"}' '${manifestPath}' > \$tmpfile && mv \$tmpfile '${manifestPath}'
                """

                        echo '✅ SharpZipLib package added successfully!'
            }
                }
            }
        }


 stage('Setup Unity Project') {
    when {
        expression { params.GAME_ENGINE == 'unity' }
    }
    steps {
        script {
            def projectPath = params.UNITY_PROJECT_PATH
            def versionFile = "${projectPath}/ProjectSettings/ProjectVersion.txt"
            def unityVersion = sh(script: "grep 'm_EditorVersion:' '${versionFile}' | awk '{print \$2}'", returnStdout: true).trim()
            def unityBinary = "/Applications/Unity/Hub/Editor/${unityVersion}/Unity.app/Contents/MacOS/Unity"

            // Export SCENE_INDEX_TO_PATCH as env variable too
            env.SCENE_INDEX_TO_PATCH = "${params.SCENE_INDEX_TO_PATCH}"

            echo "⚡ Setup Unity Project: ${unityVersion}"
            echo "📌 SCRIPT_TO_PATCH set to: ${env.SCRIPT_TO_PATCH}"
            echo "📌 SCENE_INDEX_TO_PATCH set to: ${env.SCENE_INDEX_TO_PATCH}"

            // ✅ Inject environment variables directly into Unity execution context
            sh """
                SCRIPT_TO_PATCH='${env.SCRIPT_TO_PATCH}' \\
                SCENE_INDEX_TO_PATCH='${env.SCENE_INDEX_TO_PATCH}' \\
                '${unityBinary}' -quit -batchmode -projectPath '${projectPath}' \\
                -executeMethod SetupUnityProject.SetupProjectForSDK
            """

            echo '✅ Unity Project Setup Done successfully!'
        }
    }
}


        stage('Trigger Unity Compilation (Auto Detect Unity Version)') {
            when {
                expression { params.GAME_ENGINE == 'unity' }
            }
            steps {
                script {
                    def projectPath = params.UNITY_PROJECT_PATH
                    def versionFile = "${projectPath}/ProjectSettings/ProjectVersion.txt"

                    echo "Reading Unity version from: ${versionFile}"

                    def unityVersion = sh(
                script: "grep 'm_EditorVersion:' '${versionFile}' | awk '{print \$2}'",
                returnStdout: true
            ).trim()

                    echo "Detected Unity version: ${unityVersion}"

                    def unityPath = "/Applications/Unity/Hub/Editor/${unityVersion}/Unity.app/Contents/MacOS/Unity"

                    echo "Triggering Unity (${unityVersion}) to refresh and compile the project using binary at: ${unityPath}"

                    sh """
                '${unityPath}' -quit -batchmode -projectPath '${projectPath}'
            """

                    echo "✅ Unity compilation triggered successfully with version ${unityVersion}."
                }
            }
        }

        stage('Parallel Tasks') {
            parallel {
                stage('Build Cocos Project') {
                    when {
                        expression { params.GAME_ENGINE == 'unity' && params.COCOS_VERSION == 'cocos2' }
                    }
                    steps {
                        script {
                            echo '🚀 Preparing Cocos project build...'

                            // Define Cocos Creator executable path
                            def cocosCreatorPath = '/Applications/CocosCreator-2.1.3.app/Contents/MacOS/CocosCreator'

                            // Clean old build folder if it exists
                            def oldBuildPath = "${params.COCOS_PROJECT_PATH}/build"
                            echo "🧹 Checking and cleaning old build at: ${oldBuildPath}"

                            sh """
                if [ -d '${oldBuildPath}' ]; then
                    echo "🗑️ Old build found. Deleting..."
                    rm -rf '${oldBuildPath}'
                else
                    echo "✅ No old build to clean."
                fi
            """

                            // Start building
                            echo '🚀 Starting fresh Cocos project build...'
                            sh """
                '${cocosCreatorPath}' --path '${params.COCOS_PROJECT_PATH}' --build "platform=ios;debug=false"
            """

                            echo '✅ Cocos project build completed!'
                        }
                    }
                }

                stage('Build Unity Project') {
                    when {
                        expression { params.GAME_ENGINE == 'unity' }
                    }
                    steps {
                        script {
                            def projectPath = params.UNITY_PROJECT_PATH
                            def versionFile = "${projectPath}/ProjectSettings/ProjectVersion.txt"

                            def unityVersion = sh(script: "grep 'm_EditorVersion:' '${versionFile}' | awk '{print \$2}'", returnStdout: true).trim()
                            def unityBinary = "/Applications/Unity/Hub/Editor/${unityVersion}/Unity.app/Contents/MacOS/Unity"

                            echo "Detected Unity version: ${unityVersion}"
                            echo "Starting Unity build using binary: ${unityBinary}"

                            sh """
                '${unityBinary}' -quit -batchmode -projectPath '${projectPath}' -executeMethod BuildHelper.PerformBuild
            """

                            echo '✅ Unity build completed successfully.'
                        }
                    }
                }
            }
        }

        stage('Run changeLibCC Script After Cocos Build') {
            when {
                expression { params.GAME_ENGINE == 'unity' && params.COCOS_VERSION == 'cocos2' }
            }
            steps {
                script {
                    def scriptPath = "${params.COCOS_PROJECT_PATH}/changeLibCC"

                    // Ensure it's executable
                    sh "chmod +x '${scriptPath}'"

                    // Run the script
                    sh "'${scriptPath}'"

                    echo '✅ changeLibCC script executed successfully.'
                }
            }
        }
        stage('Cleanup Unity Editor Scripts') {
            when {
                expression { params.GAME_ENGINE == 'unity' }
            }
            steps {
                script {
                    def targetEditorPath = "${params.UNITY_PROJECT_PATH}/Assets/Editor"
                    def helperScript = "${targetEditorPath}/BuildHelper.cs"
                    def unityprojectsetupscript = "${targetEditorPath}/SetupUnityProject.cs"

                    echo '🧹 Cleaning up temporary editor scripts...'

                    // Delete if exists
                    if (fileExists(helperScript)) {
                        sh "rm -f '${helperScript}'"
                        echo '✅ Deleted BuildHelper.cs'
            } else {
                        echo '⚠️ BuildHelper.cs not found, skipping'
                    }

                    if (fileExists(unityprojectsetupscript)) {
                        sh "rm -f '${unityprojectsetupscript}'"
                        echo '✅ Deleted SetupUnityProject.cs'
            } else {
                        echo '⚠️ SetupUnityProject.cs not found, skipping'
                    }

                    echo '🧼 Editor script cleanup complete.'
                }
            }
        }

        stage('Copy Cocos Build to Jenkins Build Folder') {
            when {
                expression { params.GAME_ENGINE == 'unity' && params.COCOS_VERSION == 'cocos2' }
            }
            steps {
                script {
                    def productName = sh(
                script: "grep 'productName:' '${params.UNITY_PROJECT_PATH}/ProjectSettings/ProjectSettings.asset' | sed 's/^[^:]*: *//'",
                returnStdout: true
            ).trim()

                    def cocosBuildFolder = "${params.COCOS_PROJECT_PATH}/build"
                    def targetBuildFolder = "$HOME/jenkinsBuild/${productName}/CocosBuild"

                    echo "📂 Copying Cocos build from ${cocosBuildFolder} to ${targetBuildFolder}"

                    // Clean target and copy build
                    sh """
                rm -rf '${targetBuildFolder}'
                mkdir -p '${targetBuildFolder}'
                cp -R '${cocosBuildFolder}/.' '${targetBuildFolder}/'
            """

                    echo '✅ Cocos build copied successfully.'

                    // Optional: delete the original Cocos build
                    sh """
                rm -rf '${cocosBuildFolder}'
            """

                    echo '🧹 Original Cocos build folder deleted to save space.'
                }
            }
        }

        stage('Check Go Version') {
            steps {
                sh 'go version'
            }
        }
        
     stage('Patch Unity Xcode Project') {
              when {
                expression { params.GAME_ENGINE == 'unity'}
            }
    steps {
        script {
            def productName = sh(
                script: "grep 'productName:' '${params.UNITY_PROJECT_PATH}/ProjectSettings/ProjectSettings.asset' | sed 's/^[^:]*: *//'",
                returnStdout: true
            ).trim()

            def jenkinsfiles = "${env.WORKSPACE}/JenkinsFiles"

            def targetBuildFolder = "${env.HOME_DIR}/jenkinsBuild/${productName}"
            def patchScript = 'updateUnityXcodeProj.go'
            def privacyFile = 'PrivacyInfo.xcprivacy'

            sh """
                set +e
                mkdir -p '${targetBuildFolder}'
                cp '${jenkinsfiles}/Golang/${patchScript}' '${targetBuildFolder}/'
                cp '${jenkinsfiles}/${privacyFile}' '${targetBuildFolder}/'
                cd '${targetBuildFolder}'
                go mod init patchproject
                go get howett.net/plist
                go build -o patch_unity_xcode ${patchScript}
                ./patch_unity_xcode
                BUILD_RESULT=\$?
                echo '🔍 Dumping log before cleanup:'
                cat /tmp/unity_xcode_patch.log || echo '⚠️ No log found.'
                rm -f ${patchScript} ${privacyFile} patch_unity_xcode go.mod go.sum
                exit \$BUILD_RESULT
            """
        }
    }
}




        stage('Setup Xcode Workspace (Unity + Cocos)') {
            when {
                expression { params.GAME_ENGINE == 'unity' && params.COCOS_VERSION == 'cocos2' }
            }
            steps {
                script {
                    echo '🔎 Searching for Python virtual environment...'

                    def venvPath = sh(
                script: "find \$HOME/.venvs -name 'pbxproj-env' -type d | head -n 1",
                returnStdout: true
            ).trim()

                    if (!venvPath) {
                        error "❌ Virtual environment 'pbxproj-env' not found!"
                    }
                    echo "✅ Found VENV at: ${venvPath}"
                    def pythonfiles = "${env.WORKSPACE}/JenkinsFiles/Python"

                    def sourcePyScript = "${pythonfiles}/SetupXcodeWorkspace.py"
                    def targetFolder = "${params.UNITY_PROJECT_PATH}/unityBuild"
                    def copiedScript = "${targetFolder}/SetupXcodeWorkspace.py"

                    echo "📁 Copying SetupXcodeWorkspace.py to: ${targetFolder}"
                    sh """
                mkdir -p '${targetFolder}'
                cp '${sourcePyScript}' '${copiedScript}'
            """
                    echo '✅ Script copied.'

                    def productName = sh(
                script: "grep 'productName:' '${params.UNITY_PROJECT_PATH}/ProjectSettings/ProjectSettings.asset' | sed 's/^[^:]*: *//'",
                returnStdout: true
            ).trim()

                    def sanitizedName = productName.replaceAll(/[^a-zA-Z0-9]/, '')
                    def unityXcodeProj = "/Users/meher/jenkinsBuild/${productName}/UnityBuild/Unity-iPhone.xcodeproj"
                    def cocosXcodeProj = "/Users/meher/jenkinsBuild/${productName}/CocosBuild/jsb-default/frameworks/runtime-src/proj.ios_mac/${sanitizedName}.xcodeproj"

                    if (!fileExists(unityXcodeProj)) {
                        error "❌ Unity Xcode project not found at: ${unityXcodeProj}"
                    }
                    if (!fileExists(cocosXcodeProj)) {
                        error "❌ Cocos Xcode project not found at: ${cocosXcodeProj}"
                    }

                    echo '🚀 Running SetupXcodeWorkspace.py...'
                    sh """
                source '${venvPath}/bin/activate' && \
                python3 '${copiedScript}' '${unityXcodeProj}' '${cocosXcodeProj}'
            """

                    // 🔥 Delete copied Python script
                    sh "rm -f '${copiedScript}'"
                    echo '🧹 Cleanup: Deleted SetupXcodeWorkspace.py'

                    echo '🎉 Workspace with Unity and Cocos projects created successfully!'
                }
            }
        }

        stage('Replace Cocos iOS Icons with Unity Icons') {
            when {
                expression { params.GAME_ENGINE == 'unity' && params.COCOS_VERSION == 'cocos2' }
            }
            steps {
                script {
                    echo '🔎 Reading product name from Unity settings...'

                    def productName = sh(
                script: "grep 'productName:' '${params.UNITY_PROJECT_PATH}/ProjectSettings/ProjectSettings.asset' | sed 's/^[^:]*: *//'",
                returnStdout: true
            ).trim()

                    def sanitizedProductName = productName.replaceAll(/[^A-Za-z0-9]/, '')

                    def unityIconsPath = "${env.HOME}/jenkinsBuild/${productName}/UnityBuild/Unity-iPhone/Images.xcassets/AppIcon.appiconset"
                    def cocosIconsPath = "${env.HOME}/jenkinsBuild/${productName}/CocosBuild/jsb-default/frameworks/runtime-src/proj.ios_mac/ios/Images.xcassets/AppIcon.appiconset"

                    if (!fileExists(unityIconsPath)) {
                        error "❌ Unity AppIcon path not found: ${unityIconsPath}"
                    }

                    echo "🔁 Replacing Cocos icons using Unity's icon set..."
                    sh """
                rm -rf "${cocosIconsPath}"
                cp -R "${unityIconsPath}" "${cocosIconsPath}"
            """

                    echo '✅ Cocos iOS app icons successfully replaced!'
                }
            }
        }

        stage('Copy functionsMap.json to Cocos Build') {
    when {
        expression { params.GAME_ENGINE == 'unity' && params.COCOS_VERSION == 'cocos2' }
    }
    steps {
        script {
            def productName = sh(
                script: "grep 'productName:' '${params.UNITY_PROJECT_PATH}/ProjectSettings/ProjectSettings.asset' | sed 's/^[^:]*: *//'",
                returnStdout: true
            ).trim()

            def buildpath = "$HOME/jenkinsBuild/${productName}"
            def sourceJsonPath = "${params.PLUGINS_PROJECT_PATH}/functionsMap.json"
            def targetJsonPath = "${buildpath}/functionsMap.json"

            if (!fileExists(sourceJsonPath)) {
                error "❌ Missing functionsMap.json at: ${sourceJsonPath}"
            }

            echo "📁 Copying functionsMap.json to ${targetJsonPath}"
            sh "cp '${sourceJsonPath}' '${targetJsonPath}'"
            echo "✅ functionsMap.json copied successfully."
        }
    }
}


        stage('📂 Open Game Build Folder') {
            steps {
                script {
                    // Get the user's home directory dynamically
                    def userHome = sh(
                script: "echo \$HOME",
                returnStdout: true
            ).trim()

                    // Extract product name (unsanitized) — as used in actual build folder naming
                    def productName = sh(
                script: "grep 'productName:' '${params.UNITY_PROJECT_PATH}/ProjectSettings/ProjectSettings.asset' | sed 's/^[^:]*: *//'",
                returnStdout: true
            ).trim()

                    // Final folder path where all builds and WS were placed
                    def buildRootPath = "${userHome}/jenkinsBuild/${productName}"

                    echo "📂 Opening game build folder: ${buildRootPath}"

                    sh """
                open "${buildRootPath}"
            """
                }
            }
        }
    }
}
