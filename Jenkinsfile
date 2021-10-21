pipeline {

    agent any

    stages {
        stage('Test') {
             steps {
                 withCredentials([
                     string(credentialsId: 'ADDRESS_SONAR', variable: 'address_sonar'),
                     string(credentialsId: 'SONAR_CORE_TOKEN', variable: 'core_token')]){
                    sh './gradlew clean test sonarqube -Dsonar.projectKey=iexec-core -Dsonar.host.url=$address_sonar -Dsonar.login=$core_token --refresh-dependencies --no-daemon -i'
                 }
                 junit 'build/test-results/**/*.xml'
             }
        }

        stage('Build') {
            steps {
                sh './gradlew build --refresh-dependencies --no-daemon'
            }
        }

        stage('Upload Jars') {
            when {
                anyOf{
                    branch 'master'
                    branch 'develop'
                }
            }
            steps {
                withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'nexus', usernameVariable: 'NEXUS_USER', passwordVariable: 'NEXUS_PASSWORD']]) {
                    sh './gradlew -PnexusUser=$NEXUS_USER -PnexusPassword=$NEXUS_PASSWORD uploadArchives --no-daemon'
                }
            }
        }
        stage('Build/Upload Docker image') {
            when {
                anyOf{
                    branch 'master'
                    branch 'develop'
                }
            }
            steps {
                withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'nexus', usernameVariable: 'NEXUS_USER', passwordVariable: 'NEXUS_PASSWORD']]) {
                    sh './gradlew -PnexusUser=$NEXUS_USER -PnexusPassword=$NEXUS_PASSWORD pushImage --no-daemon'
                }
            }
        }
    }

}
