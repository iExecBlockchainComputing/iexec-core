pipeline {

    agent any

    stages {
        stage('Test') {
            steps {
                 sh './gradlew clean test --refresh-dependencies'
            }
        }

        stage('Build') {
            steps {
                sh './gradlew build --refresh-dependencies'
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
                        sh './gradlew -PnexusUser=$NEXUS_USER -PnexusPassword=$NEXUS_PASSWORD pushImage'
                    }
              }
        }
    }

    post {
        always {
            junit 'build/test-results/test/*.xml'
        }
    }
}
