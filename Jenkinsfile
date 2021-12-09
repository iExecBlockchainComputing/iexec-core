@Library('global-jenkins-library@1.3.0') _
buildJavaProject(
        integrationTestsEnvVars: [],
        shouldPublishJars: true,
        shouldPublishDockerImages: true,
        dockerfileDir: '.',
        //dockerfileFilename: "Dockerfile-local",
        buildContext: '.',
        //dockerImageRepositoryName: '',
        preProductionVisibility: 'docker.io',
        productionVisibility: 'docker.io')
