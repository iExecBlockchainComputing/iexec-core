void setBuildStatus(String message, String state) {
  step([
      $class: "GitHubCommitStatusSetter",
      reposSource: [$class: "ManuallyEnteredRepositorySource", url: "https://github.com/iExecBlockchainComputing/iexec-core"],
      contextSource: [$class: "ManuallyEnteredCommitContextSource", context: "ci/jenkins/build-status"],
      errorHandlers: [[$class: "ChangingBuildStatusErrorHandler", result: "UNSTABLE"]],
      statusResultSource: [ $class: "ConditionalStatusResultSource", results: [[$class: "AnyBuildResult", message: message, state: state]] ]
  ]);
}

pipeline {

    agent any

    stages {
        stage('Build + Tests') {
          steps {
                withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'nexus', usernameVariable: 'NEXUS_USER', passwordVariable: 'NEXUS_PASSWORD']]) {
                    sh './gradlew -PnexusUser=$NEXUS_USER -PnexusPassword=$NEXUS_PASSWORD clean build --refresh-dependencies'
                }
            }
        }
        stage('Upload Jars') {
              steps {
                    withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'nexus', usernameVariable: 'NEXUS_USER', passwordVariable: 'NEXUS_PASSWORD']]) {
                        sh './gradlew -PnexusUser=$NEXUS_USER -PnexusPassword=$NEXUS_PASSWORD uploadArchives'
                    }
              }
        }
        stage('Build/Upload Docker image') {
              steps {
                    withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'nexus', usernameVariable: 'NEXUS_USER', passwordVariable: 'NEXUS_PASSWORD']]) {
                        sh './gradlew -PnexusUser=$NEXUS_USER -PnexusPassword=$NEXUS_PASSWORD pushImage -PforceDockerBuild'
                    }
              }
        }
    }

    post {
        success {
            setBuildStatus("Build succeeded", "SUCCESS");
        }
        failure {
            setBuildStatus("Build failed", "FAILURE");
        }
        always {
            junit 'build/test-results/test/*.xml'
        }
    }


}
