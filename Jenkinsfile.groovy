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
                    return params.COCOS_VERSION == 'cocos2' || params.COCOS_VERSION == 'cocos3'
                }
            }
            steps {
                script {
                    checkCocosCreatorPath(
                cocosVersion: params.COCOS_VERSION
            )
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
        stage('Setup Parallel Tasks') {
            parallel {
                stage('Cocos Pipeline') {
                    stages {
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
                    }
                }
                stage('Unity Pipeline') {
                    stages {
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
                    }
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

        stage('Build Parallel Tasks') {
            parallel {
                stage('Build Cocos Project') {
                    when {
                        expression {
                            return params.GAME_ENGINE == 'unity' &&
                           (params.COCOS_VERSION == 'cocos2' || params.COCOS_VERSION == 'cocos3')
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

        stage('Copy Cocos Project to Jenkins Build Folder') {
            when {
                expression {
                    return params.GAME_ENGINE == 'unity' &&
                   (params.COCOS_VERSION == 'cocos2' || params.COCOS_VERSION == 'cocos3')
                }
            }
            steps {
                script {
                    def copiedPath = copyCocosBuildToJenkins(
                unityProjectPath: params.UNITY_PROJECT_PATH,
                cocosProjectPath: params.COCOS_PROJECT_PATH,
                cocosVersion: params.COCOS_VERSION
            )

                    echo "📦 Cocos project copied to: ${copiedPath}"
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
                    patchUnityXcodeProject(
                unityProjectPath: params.UNITY_PROJECT_PATH
            )
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
                    setupXcodeWorkspace(
                unityProjectPath: params.UNITY_PROJECT_PATH,
                cocosProjectPath: params.COCOS_PROJECT_PATH,
                cocosVersion: params.COCOS_VERSION
            )
                }
            }
        }

        stage('Replace Cocos iOS Icons with Unity Icons') {
            when {
                expression {
                    return params.GAME_ENGINE == 'unity6' &&
                   (params.COCOS_VERSION == 'cocos2' || params.COCOS_VERSION == 'cocos3')
                }
            }
            steps {
                script {
                    replaceCocosIconsWithUnity(
                unityProjectPath: params.UNITY_PROJECT_PATH,
                cocosVersion: params.COCOS_VERSION,
                targetBuildFolder: "$HOME/jenkinsBuild/${params.PRODUCT_NAME}" // optional override
            )
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
                    copyFunctionsMapToCocosBuild(
                unityProjectPath: params.UNITY_PROJECT_PATH,
                pluginsProjectPath: params.PLUGINS_PROJECT_PATH
            )
                }
            }
        }

        stage('📂 Open Game Build Folder') {
            steps {
                script {
                    openGameBuildFolder(
                unityProjectPath: params.UNITY_PROJECT_PATH
            )
                }
            }
        }
    }
}
