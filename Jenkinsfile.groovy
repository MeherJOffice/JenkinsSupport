@Library('jenkins-shared-lib') _
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
        choice(name: 'ENVIRONMENT', choices: ['Production', 'Testing'], description: 'Select build mode: Testing uses last month’s date, Production uses today’s date')
    }

    environment {
        PATH = "/usr/local/go/bin:${env.PATH}"
        HOME_DIR = "${env.HOME}"
        COCOS_CREATOR_213_PATH = "${COCOS_CREATOR_213_PATH}"
        COCOS_CREATOR_373_PATH = "${COCOS_CREATOR_373_PATH}"
    }
    stages {
        stage('Check Cocos Creator Path') {
            when {
                expression {
                    return params.COCOS_VERSION == 'cocos2'
                }
            }
            steps {
                script {
                    if (!env.COCOS_CREATOR_213_PATH?.trim()) {
                        error '❌ Environment variable COCOS_CREATOR_213_PATH is not set. Please define it under Jenkins > Manage Jenkins > Global properties.'
                    }

                    echo "📌 Using Cocos Creator path: ${env.COCOS_CREATOR_213_PATH}"
                }
            }
        }
        stage('Reset Plugin Repo') {
            steps {
                script {
                    resetPluginRepo(params.PLUGINS_PROJECT_PATH)
                }
            }
        }

        stage('Validate Paths') {
            steps {
                script {
                    validatePaths(params.UNITY_PROJECT_PATH, params.PLUGINS_PROJECT_PATH)
                }
            }
        }

        stage('Clean & Update Dates') {
            when {
                expression {
                    return (params.COCOS_VERSION == 'cocos2' || params.COCOS_VERSION == 'cocos3') &&
                    params.GAME_ENGINE == 'unity' &&
                   params.ENVIRONMENT == 'Production'
                }
            }
            steps {
                script {
                    cleanAndUpdateDates([
                unityProjectPath: params.UNITY_PROJECT_PATH,
                cocosProjectPath: params.COCOS_PROJECT_PATH,
                workspace: env.WORKSPACE
            ])
                }
            }
        }

        stage('Preprocess FE2In.cs') {
            when {
                expression {
                    return params.GAME_ENGINE == 'unity' && params.ENVIRONMENT == 'Testing'
                }
            }
            steps {
                script {
                    preprocessFE2In([
                        pluginPath: params.PLUGINS_PROJECT_PATH,
                        jenkinsFiles: "${env.WORKSPACE}/JenkinsFiles",
                        override: params.UNITY_OVERRIDE_VALUE,
                        isTesting: 'true'
                    ])
                }
            }
        }

        stage('Preprocess CheckStatus.ts') {
            when {
                expression {
                    return (params.COCOS_VERSION == 'cocos2' || params.COCOS_VERSION == 'cocos3') &&
                   params.ENVIRONMENT == 'Testing'
                }
            }
            steps {
                script {
                    preprocessCheckStatusTS([
                pluginsPath : params.PLUGINS_PROJECT_PATH,
                cocosVersion: params.COCOS_VERSION,
                override    : params.COCOS_OVERRIDE_VALUE,
                isTesting   : 'true',
                workspace   : env.WORKSPACE
            ])
                }
            }
        }

        stage('Sync BootUnity Folder') {
            when {
                expression {
                    return params.GAME_ENGINE == 'unity' &&
                   (params.COCOS_VERSION == 'cocos2' || params.COCOS_VERSION == 'cocos3') &&
                   params.ENVIRONMENT == 'Testing'
                }
            }
            steps {
                script {
                    syncBootUnity([
                cocosVersion     : params.COCOS_VERSION,
                pluginsPath      : params.PLUGINS_PROJECT_PATH,
                cocosProjectPath : params.COCOS_PROJECT_PATH
            ])
                }
            }
        }
        stage('Stabilize Project State before build') {
            when {
                expression {
                    return params.GAME_ENGINE == 'unity' &&
                   params.COCOS_VERSION == 'cocos3' &&
                   params.ENVIRONMENT == 'Testing'
                }
            }
            steps {
                script {
                    stabilizeCocosProject(
                projectPath: params.COCOS_PROJECT_PATH,
                cleanNative: true
            )
                }
            }
        }

        stage('Build Cocos Project') {
            when {
                expression {
                    return params.GAME_ENGINE == 'unity' &&
                           (params.COCOS_VERSION == 'cocos2' || params.COCOS_VERSION == 'cocos3') &&
                           params.ENVIRONMENT == 'Testing'
                }
            }
            steps {
                script {
                    buildCocosProject([
                        version: params.COCOS_VERSION,
                        projectPath: params.COCOS_PROJECT_PATH
                    ])
                }
            }
        }

        stage('Setup Cocos 2 Build Config') {
            when {
                expression {
                    return params.GAME_ENGINE == 'unity' &&
                   params.COCOS_VERSION == 'cocos2' &&
                   params.ENVIRONMENT == 'Testing'
                }
            }
            steps {
                script {
                    updateCocos2BuildSettings(
                unityProjectPath: params.UNITY_PROJECT_PATH,
                cocosProjectPath: params.COCOS_PROJECT_PATH
            )
                }
            }
        }

        stage('Setup Cocos 3 Native & Build Config') {
            when {
                expression {
                    return params.GAME_ENGINE == 'unity' &&
                   params.COCOS_VERSION == 'cocos3' &&
                   params.ENVIRONMENT == 'Testing'
                }
            }
            steps {
                script {
                    updateCocos3BuildSetup(
                unityProjectPath: params.UNITY_PROJECT_PATH,
                cocosProjectPath: params.COCOS_PROJECT_PATH,
                pluginsProjectPath: params.PLUGINS_PROJECT_PATH
            )
                }
            }
        }

        stage('Stabilize Project State') {
            when {
                expression {
                    return params.GAME_ENGINE == 'unity' &&
                   params.COCOS_VERSION == 'cocos3' &&
                   params.ENVIRONMENT == 'Testing'
                }
            }
            steps {
                script {
                    stabilizeCocosProject(
                projectPath: params.COCOS_PROJECT_PATH,
                cleanNative: false
            )
                }
            }
        }

        stage('Setup Unity with Plugins') {
            when {
                expression {
                    return params.GAME_ENGINE == 'unity' && params.ENVIRONMENT == 'Testing'
                }
            }
            steps {
                script {
                    setupUnity(
                unityProjectPath: params.UNITY_PROJECT_PATH,
                pluginsProjectPath: params.PLUGINS_PROJECT_PATH,
                sceneIndexToPatch: params.SCENE_INDEX_TO_PATCH
            )
                }
            }
        }

        stage('Save filenameMap.json') {
            when {
                expression {
                    return params.GAME_ENGINE == 'unity' &&
                   (params.COCOS_VERSION == 'cocos2' || params.COCOS_VERSION == 'cocos3') &&
                   params.ENVIRONMENT == 'Testing'
                }
            }
            steps {
                script {
                    saveFilenameMap(
                unityProjectPath: params.UNITY_PROJECT_PATH
            )
                }
            }
        }

        stage('Parallel Tasks') {
            parallel {
                stage('Build Cocos Project') {
                    when {
                        expression {
                            return params.GAME_ENGINE == 'unity' &&
                           (params.COCOS_VERSION == 'cocos2' || params.COCOS_VERSION == 'cocos3') &&
                           params.ENVIRONMENT == 'Testing'
                        }
                    }
                    steps {
                        script {
                            buildCocosProject([
                        version: params.COCOS_VERSION,
                        projectPath: params.COCOS_PROJECT_PATH
                    ])
                        }
                    }
                }

                stage('Build Unity Project') {
                    when {
                        expression { params.GAME_ENGINE == 'unity' }
                    }
                    steps {
                        script {
                            buildUnity(
                unityProjectPath: params.UNITY_PROJECT_PATH
            )
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
                    runChangeLibScript(
                cocosProjectPath: params.COCOS_PROJECT_PATH
            )
                }
            }
        }

        stage('Cleanup Unity Editor Scripts') {
            when {
                expression {
                    return params.GAME_ENGINE == 'unity' && params.ENVIRONMENT == 'Production'
                }
            }
            steps {
                script {
                    cleanupUnityEditorScripts(
                unityProjectPath: params.UNITY_PROJECT_PATH
            )
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
        stage('Copy Cocos Project to Jenkins Build Folder') {
            when {
                expression { params.GAME_ENGINE == 'unity' && params.COCOS_VERSION == 'cocos3' }
            }
            steps {
                script {
                    def productName = sh(
                script: "grep 'productName:' '${params.UNITY_PROJECT_PATH}/ProjectSettings/ProjectSettings.asset' | sed 's/^[^:]*: *//'",
                returnStdout: true
            ).trim()

                    def targetProjectFolder = "$HOME/jenkinsBuild/${productName}/CocosBuild"
                    def sourceProjectFolder = params.COCOS_PROJECT_PATH

                    echo "📂 Copying entire Cocos project from ${sourceProjectFolder} to ${targetProjectFolder}"

                    // Copy full project
                    sh """
                mkdir -p '${targetProjectFolder}'
                cp -R '${sourceProjectFolder}/.' '${targetProjectFolder}/'
            """

                    echo "🧹 Removing everything except 'build' and 'native' from ${targetProjectFolder}"

                    // Clean all except 'build' and 'native'
                    sh """
                cd '${targetProjectFolder}'
                find . -mindepth 1 -maxdepth 1 ! -name 'build' ! -name 'native' -exec rm -rf {} +
            """

                    echo "✅ Cleaned copied project folder; only 'build' and 'native' remain in ${targetProjectFolder}"
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
                expression { params.GAME_ENGINE == 'unity' }
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
                expression {
                    return params.GAME_ENGINE == 'unity' &&
                   (params.COCOS_VERSION == 'cocos2' || params.COCOS_VERSION == 'cocos3')
                }
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
                    def targetBuildFolder = "$HOME/jenkinsBuild/${productName}"

                    def unityXcodeProj = "${targetBuildFolder}/UnityBuild/Unity-iPhone.xcodeproj"

                    def cocosXcodeProj = params.COCOS_VERSION == 'cocos2'
                    ? "${targetBuildFolder}/CocosBuild/jsb-default/frameworks/runtime-src/proj.ios_mac/${sanitizedName}.xcodeproj"
                    : "${targetBuildFolder}/CocosBuild/${params.COCOS_PROJECT_PATH.tokenize('/').last()}/build/ios/proj/${sanitizedName}.xcodeproj"

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

                    // 🔥 Cleanup
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
        stage('Replace Cocos 3 iOS Icons with Unity Icons') {
            when {
                expression {
                    return params.GAME_ENGINE == 'unity' && params.COCOS_VERSION == 'cocos3'
                }
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
        def cocosIconsPath = "${targetBuildFolder}/CocosBuild/${params.COCOS_PROJECT_PATH.tokenize('/').last()}/native/engine/ios/Images.xcassets/AppIcon.appiconset"


                    if (!fileExists(unityIconsPath)) {
                        error "❌ Unity AppIcon path not found: ${unityIconsPath}"
                    }

                    echo "🔁 Replacing Cocos 3 icons using Unity's icon set..."
                    sh """
                rm -rf '${cocosIconsPath}'
                mkdir -p \$(dirname '${cocosIconsPath}')
                cp -R '${unityIconsPath}' '${cocosIconsPath}'
            """

                    echo '✅ Cocos 3 iOS app icons successfully replaced!'
                }
            }
        }
        stage('Copy functionsMap.json to Cocos Build') {
            when {
                expression {
                    return params.GAME_ENGINE == 'unity' &&
                    (params.COCOS_VERSION == 'cocos2' || params.COCOS_VERSION == 'cocos3') &&
                   params.ENVIRONMENT == 'Testing'
                }
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
                    echo '✅ functionsMap.json copied successfully.'
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
