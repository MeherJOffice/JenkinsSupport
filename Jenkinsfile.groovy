@Library('jenkins-shared-lib-native') _
pipeline {
    agent any

    parameters {
        string(name: 'PLUGINS_PROJECT_PATH', defaultValue: '/Users/meher/Documents/GitHub/UpStoreTools', description: 'Local path to Plugins repo')
        string(name: 'COCOS_PROJECT_PATH', defaultValue: '/Users/meher/Documents/GitHub/CocosProjectsSDK/TestProject', description: 'Cocos project path')
        text(name: 'COCOS_OVERRIDE_VALUE', description: 'Override value for Cocos')
        choice(name: 'COCOS_VERSION', choices: ['cocos2', 'cocos3'], description: 'Cocos version')
        choice(name: 'ENVIRONMENT', choices: ['Testing', 'Production'], description: 'Select build mode: Testing uses last month’s date, Production uses today’s date')
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
        stage('🗑️ Clean Cocos Folders') {
            when {
                expression {
                    return params.COCOS_VERSION == 'cocos2' &&
                   params.ENVIRONMENT == 'Testing'
                }
            }
            steps {
                script {
                    def cocosProjectPath = params.COCOS_PROJECT_PATH

                    echo "🧹 Deleting folders from: ${cocosProjectPath}"

                    sh """
                rm -rf '${cocosProjectPath}/build-templates'
                rm -rf '${cocosProjectPath}/node_modules'
                echo "✅ Deleted build-templates and node_modules from Cocos project"
            """
                }
            }
        }
        stage('📦 Install NPM Dependencies in Plugins') {
            steps {
                script {
                    def pluginsPath = params.PLUGINS_PROJECT_PATH

                    if (!pluginsPath) {
                        error "❌ 'PLUGINS_PROJECT_PATH' is required"
                    }

                    echo "📂 Installing NPM packages in: ${pluginsPath}"

                    dir(pluginsPath) {
                        sh 'npm install'
                    }

                    echo '✅ NPM dependencies installed successfully in plugins.'
                }
            }
        }
        stage('Preprocess CheckStatus.ts') {
            when {
                expression {
                    return params.COCOS_VERSION == 'cocos2' &&
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
        stage('Sync Boot Folder') {
            when {
                expression {
                    return params.COCOS_VERSION == 'cocos2' &&
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

        stage('Build Cocos Project meta') {
            when {
                expression {
                    return params.COCOS_VERSION == 'cocos2' &&
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
        stage('Inject Checker Node') {
            when {
                expression {
                    return params.COCOS_VERSION == 'cocos2' &&
                   params.ENVIRONMENT == 'Testing'
                }
            }
            steps {
                script {
                    def nodeScriptPath = "${workspace}/JenkinsFiles/Node/inject-node.js"
                    def targetScriptPath = "${params.COCOS_PROJECT_PATH}/inject-node.js"

                    echo '📦 Copying inject-node.js into project...'
                    sh "cp '${nodeScriptPath}' '${targetScriptPath}'"

                    echo '🚀 Running inject-node.js...'
                    dir("${params.COCOS_PROJECT_PATH}") {
                        sh 'node inject-node.js'
                    }

                    echo '🧹 Cleaning up inject-node.js...'
                    sh "rm -f '${targetScriptPath}'"
                }
            }
        }

        stage('Setup Cocos 2 Build Config') {
            when {
                expression {
                    return params.COCOS_VERSION == 'cocos2' &&
                   params.ENVIRONMENT == 'Testing'
                }
            }
            steps {
                script {
                    updateCocos2BuildSettings(
                cocosProjectPath: params.COCOS_PROJECT_PATH
            )
                }
            }
        }

        stage('Build Cocos Project') {
            when {
                expression {
                    return params.COCOS_VERSION == 'cocos2' &&
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

        stage('Run changeLibCC Script After Cocos Build') {
            when {
                expression {
                    return params.COCOS_VERSION == 'cocos2' &&
                   params.ENVIRONMENT == 'Testing'
                }
            }
            steps {
                script {
                    runChangeLibScript(
                cocosProjectPath: params.COCOS_PROJECT_PATH
            )
                }
            }
        }
        stage('Copy Cocos Project to Jenkins Build Folder') {
            when {
                expression {
                    return params.COCOS_VERSION == 'cocos2' &&
                   params.ENVIRONMENT == 'Testing'
                }
            }
            steps {
                script {
                    def copiedPath = copyCocosBuildToJenkins(
                cocosProjectPath: params.COCOS_PROJECT_PATH,
                cocosVersion: params.COCOS_VERSION
            )

                    echo "📦 Cocos 2 build copied to: ${copiedPath}"
                }
            }
        }

        stage('Check Go Version') {
            steps {
                sh 'go version'
            }
        }
        stage('📂 Open Game Build Folder') {
            steps {
                script {
                    openGameBuildFolder(
                cocosProjectPath: params.COCOS_PROJECT_PATH
            )
                }
            }
        }
    }
}
