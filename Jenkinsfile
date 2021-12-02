pipeline {

    agent any

    stages {
        stage('Test') {
             steps {
                 withCredentials([string(credentialsId: 'ADDRESS_SONAR', variable: 'address_sonar'),
                                  string(credentialsId: 'SONAR_TOKEN',   variable: 'sonar_token')]){
                    sh './gradlew clean test sonarqube -Dsonar.projectKey=iexec-core -Dsonar.host.url=$address_sonar -Dsonar.login=$sonar_token --refresh-dependencies --no-daemon'
                 }
                 junit 'build/test-results/**/*.xml'
                 jacoco()
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
                    sh './gradlew -PnexusUser=$NEXUS_USER -PnexusPassword=$NEXUS_PASSWORD publish --no-daemon'
                }
            }
        }
        stage('Build/Upload Docker image') {
            when {
                anyOf {
                    branch 'master'
                    branch 'develop'
                    branch 'feature/*'
                }
            }
            steps {
                sh './gradlew buildImage --no-daemon'
                script {
                    String ociImageNameFull = sh (
                        script: '''#!/bin/bash
                        set -o pipefail && ./gradlew properties | grep ociImageNameFull | cut -d" " -f2
                        ''',
                        returnStdout: true
                    ).trim()
                    String ociImageNameShortCommit = sh (
                        script: '''#!/bin/bash
                        set -o pipefail && ./gradlew properties | grep ociImageNameShortCommit | cut -d" " -f2
                        ''',
                        returnStdout: true
                    ).trim()
                    docker.withRegistry('https://nexus.iex.ec', 'nexus') {
                        sh """#!/bin/bash
                        docker tag  ${ociImageNameFull} ${ociImageNameShortCommit}
                        docker push ${ociImageNameShortCommit}
                        docker push ${ociImageNameFull}
                        """
                    }
                }
            }
        }
    }

}
