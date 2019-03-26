pipeline {

    agent any

    stages {
        stage('Build') {
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
        stage('Upload Docker images') {
              steps {
                    withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'nexus', usernameVariable: 'NEXUS_USER', passwordVariable: 'NEXUS_PASSWORD']]) {
                        sh './gradlew -PnexusUser=$NEXUS_USER -PnexusPassword=$NEXUS_PASSWORD pushImage -PforceDockerBuild'
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
