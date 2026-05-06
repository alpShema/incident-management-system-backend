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
        AWS_REGION        = 'eu-west-1'
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

        stage('Push to ECR') {
            when {
                anyOf {
                    branch 'develop'
                    branch 'testing'
                }
            }
            steps {
                withCredentials([string(credentialsId: 'aws-account-id', variable: 'AWS_ACCOUNT_ID')]) {
                    withAWS(credentials: 'aws-credentials', region: "${AWS_REGION}") {
                        sh '''
                            ECR_URL="${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"
                            ECR_REPO="${ECR_URL}/${appName}"
                            aws ecr get-login-password --region "${AWS_REGION}" | \
                                docker login --username AWS --password-stdin "${ECR_URL}"
                            docker tag "${appName}:${IMAGE_TAG}" "${ECR_REPO}:${IMAGE_TAG}"
                            docker tag "${appName}:${IMAGE_TAG}" "${ECR_REPO}:latest"
                            docker push "${ECR_REPO}:${IMAGE_TAG}"
                            docker push "${ECR_REPO}:latest"
                        '''
                    }
                }
            }
        }

        stage('Deploy to Staging') {
            when {
                branch 'develop'
            }
            steps {
                withCredentials([
                    string(credentialsId: 'aws-account-id', variable: 'AWS_ACCOUNT_ID'),
                    string(credentialsId: 'staging-ec2-ip',  variable: 'EC2_IP'),
                    sshUserPrivateKey(credentialsId: 'staging-ssh-key', keyFileVariable: 'SSH_KEY')
                ]) {
                    withAWS(credentials: 'aws-credentials', region: "${AWS_REGION}") {
                        sh '''
                            ECR_URL="${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"
                            ECR_REPO="${ECR_URL}/${appName}"
                            SSH_OPTS="-o StrictHostKeyChecking=no -o BatchMode=yes -o ConnectTimeout=30"

                            # Get ECR login token on Jenkins (has AWS credentials) and
                            # pipe it directly to Docker on the EC2 over SSH.
                            # The staging server needs no AWS credentials of its own.
                            ECR_TOKEN=$(aws ecr get-login-password --region "${AWS_REGION}")
                            echo "${ECR_TOKEN}" | ssh ${SSH_OPTS} -i "${SSH_KEY}" "ubuntu@${EC2_IP}" \
                                "docker login --username AWS --password-stdin ${ECR_URL}"

                            # Update the backend image tag in the server .env file
                            ssh ${SSH_OPTS} -i "${SSH_KEY}" "ubuntu@${EC2_IP}" \
                                "sed -i 's|^BACKEND_IMAGE=.*|BACKEND_IMAGE=${ECR_REPO}:${IMAGE_TAG}|' /home/ubuntu/app/.env"

                            # Pull the new image and restart only the backend container
                            ssh ${SSH_OPTS} -i "${SSH_KEY}" "ubuntu@${EC2_IP}" \
                                "cd /home/ubuntu/app && docker compose pull backend && docker compose up -d --no-deps backend"
                        '''
                    }
                }
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
