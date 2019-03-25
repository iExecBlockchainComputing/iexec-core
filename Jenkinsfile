pipeline {

    agent any

    stages {
        stage('Build') {
          steps {
                withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'nexus', usernameVariable: 'NEXUS_USER', passwordVariable: 'NEXUS_PASSWORD']]) {
                    sh './gradlew -PnexusUser=$NEXUS_USER -PnexusPassword=$NEXUS_PASSWORD clean build uploadArchives'
                }
            }
        }
    }

    post {
        always {
            junit 'build/reports/**/*.xml'
        }
    }
}
