pipeline {
    agent any

    tools {
        jdk 'jdk21'
        maven 'Maven 3.9.9'
        'hudson.plugins.sonar.SonarRunnerInstallation' 'SonarQube'
    }

    environment {
        appName           = 'hilfe-v2-backend'
        DATE              = sh(script: 'date +%Y%m%d', returnStdout: true).trim()
        IMAGE_TAG         = "${appName}-${DATE}-${BUILD_NUMBER}"
        SONAR_PROJECT_KEY = 'hilfe-v2-backend'
    }

    stages {

        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Install Dependencies') {
            steps {
                sh '''
                    java -version
                    mvn --version
                    mvn dependency:go-offline -q
                '''
            }
        }

        stage('Unit Tests') {
            when {
                changeRequest target: 'develop'
            }
            steps {
                sh 'mvn verify'
                junit allowEmptyResults: true, testResults: 'target/surefire-reports/**/*.xml'
            }
        }

        stage('SonarQube Code Analysis') {
            when {
                changeRequest target: 'develop'
            }
            steps {
                withSonarQubeEnv('SonarQube') {
                    sh 'mvn sonar:sonar -Dsonar.projectKey=${SONAR_PROJECT_KEY} -Dsonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml'
                }
            }
        }

        stage('SonarQube Code Quality') {
            when {
                changeRequest target: 'develop'
            }
            steps {
                waitForQualityGate abortPipeline: true
            }
        }

        stage('Build Verification') {
            when {
                anyOf {
                    branch 'develop'
                    branch 'testing'
                }
            }
            steps {
                sh 'mvn package -DskipTests'
            }
        }

        stage('OWASP Dependency Check') {
            when {
                branch 'testing'
            }
            steps {
                dependencyCheck additionalArguments: '--scan pom.xml --format HTML --out dependency-check-report', odcInstallation: 'OWASP-DC'
                dependencyCheckPublisher pattern: 'dependency-check-report/dependency-check-report.html'
            }
        }

        stage('Docker Build') {
            when {
                anyOf {
                    branch 'develop'
                    branch 'testing'
                }
            }
            steps {
                script {
                    sh 'docker system prune -af --volumes'
                    docker.build("${appName}:${IMAGE_TAG}")
                }
            }
        }

        stage('Trivy Security Scan') {
            when {
                anyOf {
                    branch 'develop'
                    branch 'testing'
                }
            }
            steps {
                sh "trivy image --timeout 30m --exit-code 0 --skip-dirs .git --scanners vuln --format table ${appName}:${IMAGE_TAG} > trivy-image-scan.txt"
            }
        }

    }

    post {
        always {
            script {
                try { cleanWs() } catch (err) { echo "Workspace cleanup skipped: ${err.message}" }
            }
        }
        success {
            echo "PASSED: ${env.BRANCH_NAME} | ${IMAGE_TAG}"
        }
        failure {
            echo "FAILED: ${env.BRANCH_NAME} | ${IMAGE_TAG} | ${env.BUILD_URL}"
        }
    }
}
