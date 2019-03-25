pipeline {

    agent any

    stages {
        stage('Build') {
            steps {
                script {
                    sh './gradlew clean build -x test' //run a gradle task
                }
            }
        }
    }
}
