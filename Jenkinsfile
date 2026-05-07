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

        // ── 1. Checkout ────────────────────────────────────────── all branches ──
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        // ── 2. Install Dependencies ────────────────────────────── all branches ──
        stage('Install Dependencies') {
            steps {
                sh '''
                    java -version
                    mvn --version
                    mvn dependency:go-offline -q
                '''
            }
        }

        // ── 3. Unit Tests + JaCoCo Coverage ───────────────────── all branches ──
        stage('Unit Tests') {
            steps {
                sh 'mvn verify'
                junit allowEmptyResults: true, testResults: 'target/surefire-reports/**/*.xml'
            }
        }

        // ── 4. SonarQube Analysis ────────── PR→develop | develop | testing | staging ──
        stage('SonarQube Code Analysis') {
            when {
                anyOf {
                    changeRequest target: 'develop'
                    branch 'develop'
                    branch 'testing'
                    branch 'staging'
                }
            }
            steps {
                withSonarQubeEnv('SonarQube') {
                    sh 'mvn sonar:sonar -Dsonar.projectKey=${SONAR_PROJECT_KEY} -Dsonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml'
                }
            }
        }

        // ── 5. SonarQube Quality Gate ────── PR→develop | develop | testing | staging ──
        stage('SonarQube Code Quality') {
            when {
                anyOf {
                    changeRequest target: 'develop'
                    branch 'develop'
                    branch 'testing'
                    branch 'staging'
                }
            }
            steps {
                waitForQualityGate abortPipeline: true
            }
        }

        // ── 6. OWASP Dependency Check ──────────────────────────── testing only ──
        stage('OWASP Dependency Check') {
            when {
                branch 'testing'
            }
            steps {
                dependencyCheck additionalArguments: '--scan pom.xml --format HTML --out dependency-check-report', odcInstallation: 'OWASP-DC'
                dependencyCheckPublisher pattern: 'dependency-check-report/dependency-check-report.html'
            }
        }

        // ── 7. Docker Build ────────────────────────────────────── all branches ──
        stage('Docker Build') {
            steps {
                script {
                    sh 'docker system prune -af --volumes'
                    docker.build("${appName}:${IMAGE_TAG}")
                }
            }
        }

        // ── 8. Trivy Security Scan ─────────────────────────────── all branches ──
        stage('Trivy Security Scan') {
            steps {
                sh """
                    if ! command -v trivy &>/dev/null; then
                        echo "Trivy not found — installing..."
                        curl -sfL https://raw.githubusercontent.com/aquasecurity/trivy/main/contrib/install.sh | sh -s -- -b /usr/local/bin
                    fi
                    trivy image --timeout 30m --exit-code 0 --skip-dirs .git --scanners vuln --format table ${appName}:${IMAGE_TAG} > trivy-image-scan.txt
                    cat trivy-image-scan.txt
                """
            }
        }

        // ── 9. Push to ECR ─────────────────────────────────────── staging only ──
        stage('Push to ECR') {
            when {
                branch 'staging'
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

        // ── 10. Deploy to Staging EC2 ──────────────────────────── staging only ──
        stage('Deploy to Staging') {
            when {
                branch 'staging'
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

                            ECR_TOKEN=$(aws ecr get-login-password --region "${AWS_REGION}")
                            echo "${ECR_TOKEN}" | ssh ${SSH_OPTS} -i "${SSH_KEY}" "ubuntu@${EC2_IP}" \
                                "docker login --username AWS --password-stdin ${ECR_URL}"

                            ssh ${SSH_OPTS} -i "${SSH_KEY}" "ubuntu@${EC2_IP}" \
                                "sed -i 's|^BACKEND_IMAGE=.*|BACKEND_IMAGE=${ECR_REPO}:${IMAGE_TAG}|' /home/ubuntu/app/.env"

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
