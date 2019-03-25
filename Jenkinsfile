pipeline {

    agent any

    stages {
        stage('Build') {
            steps {
                script {
                    sh './gradlew clean build' //run a gradle task
                }
            }
        }
    }
}
