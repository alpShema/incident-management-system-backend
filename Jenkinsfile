pipeline {
    agent any

    tools {
        jdk 'jdk21'
        maven 'Maven 3.9.9'
    }

    environment {
        appName           = 'hilfe-v2-backend'
        SONAR_PROJECT_KEY = 'hilfe-v2-backend'
        AWS_REGION        = 'eu-west-1'
    }

    stages {

        // ── 0. Clean Workspace ─────────────────────────────────── all branches ──
        stage('Clean Workspace') {
            steps {
                cleanWs()
            }
        }

        // ── 1. Checkout ────────────────────────────────────────── all branches ──
        stage('Checkout') {
            steps {
                checkout scm
                sh 'test -f pom.xml || (echo "ERROR: checkout did not populate workspace — pom.xml missing" && exit 1)'
                script {
                    env.DATE      = sh(script: 'date +%Y%m%d', returnStdout: true).trim()
                    env.IMAGE_TAG = "${env.appName}-${env.DATE}-${env.BUILD_NUMBER}"
                }
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

        // ── 4 & 5. SonarQube Analysis + Quality Gate ── PR→develop | testing | staging ──
        stage('SonarQube Analysis & Quality Gate') {
            when {
                expression {
                    env.CHANGE_TARGET == 'develop' ||
                    env.BRANCH_NAME == 'testing' ||
                    env.BRANCH_NAME == 'staging'
                }
            }
            steps {
                withSonarQubeEnv('SonarQube') {
                    sh 'mvn sonar:sonar -Dsonar.projectKey=hilfe-v2-backend -Dsonar.projectName="Hilfe v2 Backend"'
                }
                timeout(time: 5, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: false
                }
            }
        }

        // ── 6. Docker Build ────────────────────────────────────── all branches ──
        stage('Docker Build') {
            steps {
                script {
                    sh 'docker system prune -af --volumes'
                    docker.build("${env.appName}:${env.IMAGE_TAG}")
                }
            }
        }

        // ── 8. Trivy Security Scan ─────────────────────────────── all branches ──
        stage('Trivy Security Scan') {
            steps {
                script {
                    sh """
                        if ! command -v trivy &>/dev/null; then
                            echo "Trivy not found — installing..."
                            curl -sfL https://raw.githubusercontent.com/aquasecurity/trivy/main/contrib/install.sh | sh -s -- -b /usr/local/bin
                        fi
                        trivy image --timeout 30m --exit-code 0 --skip-dirs .git --scanners vuln --format table ${env.appName}:${env.IMAGE_TAG} > trivy-image-scan.txt
                        cat trivy-image-scan.txt
                    """
                }
            }
        }

        // ── 9. Push to ECR ─────────────────────────────────────── develop only ──
        stage('Push to ECR') {
            when {
                branch 'develop'
            }
            steps {
                script {
                    withCredentials([usernamePassword(credentialsId: 'aws-credentials', usernameVariable: 'AWS_ACCESS_KEY_ID', passwordVariable: 'AWS_SECRET_ACCESS_KEY')]) {
                        def appName  = env.appName
                        def imageTag = env.IMAGE_TAG
                        def region   = env.AWS_REGION
                        withEnv(["AWS_DEFAULT_REGION=${region}"]) {
                        sh """
                            export AWS_DEFAULT_REGION="${region}"
                            AWS_ACCOUNT_ID=\$(aws sts get-caller-identity --query Account --output text)
                            ECR_URL="\${AWS_ACCOUNT_ID}.dkr.ecr.${region}.amazonaws.com"
                            ECR_REPO="\${ECR_URL}/${appName}"
                            aws ecr get-login-password --region "${region}" | \\
                                docker login --username AWS --password-stdin "\${ECR_URL}"
                            docker tag "${appName}:${imageTag}" "\${ECR_REPO}:${imageTag}"
                            docker tag "${appName}:${imageTag}" "\${ECR_REPO}:latest"
                            docker push "\${ECR_REPO}:${imageTag}"
                            docker push "\${ECR_REPO}:latest"
                        """
                        } // withEnv
                    }
                }
            }
        }

        // ── 10. Deploy to Staging EC2 ──────────────────────────── develop only ──
        stage('Deploy to Staging') {
            when {
                branch 'develop'
            }
            steps {
                script {
                    withCredentials([usernamePassword(credentialsId: 'aws-credentials', usernameVariable: 'AWS_ACCESS_KEY_ID', passwordVariable: 'AWS_SECRET_ACCESS_KEY')]) {
                    withCredentials([string(credentialsId: 'staging-backend-ec2-ip', variable: 'EC2_IP')]) {
                    withCredentials([sshUserPrivateKey(credentialsId: 'staging-ssh-key', keyFileVariable: 'SSH_KEY')]) {
                        def appName  = env.appName
                        def imageTag = env.IMAGE_TAG
                        def region   = env.AWS_REGION
                        withEnv(["AWS_DEFAULT_REGION=${region}"]) {
                        sh """
                            export AWS_DEFAULT_REGION="${region}"
                            AWS_ACCOUNT_ID=\$(aws sts get-caller-identity --query Account --output text)
                            ECR_URL="\${AWS_ACCOUNT_ID}.dkr.ecr.${region}.amazonaws.com"
                            ECR_REPO="\${ECR_URL}/${appName}"
                            SSH_OPTS="-o StrictHostKeyChecking=no -o BatchMode=yes -o ConnectTimeout=30"

                            ECR_TOKEN=\$(aws ecr get-login-password --region "${region}")
                            echo "\${ECR_TOKEN}" | ssh \${SSH_OPTS} -i "\${SSH_KEY}" "ubuntu@\${EC2_IP}" \\
                                "docker login --username AWS --password-stdin \${ECR_URL}"

                            ssh \${SSH_OPTS} -i "\${SSH_KEY}" "ubuntu@\${EC2_IP}" \\
                                "mkdir -p /home/ubuntu/app && \
                                 touch /home/ubuntu/app/.env && \
                                 sed -i '/^BACKEND_IMAGE=/d' /home/ubuntu/app/.env && \
                                 echo 'BACKEND_IMAGE=\${ECR_REPO}:${imageTag}' >> /home/ubuntu/app/.env"

                            scp \${SSH_OPTS} -i "\${SSH_KEY}" docker-compose.yml "ubuntu@\${EC2_IP}:/home/ubuntu/app/docker-compose.yml"

                            ssh \${SSH_OPTS} -i "\${SSH_KEY}" "ubuntu@\${EC2_IP}" \\
                                "cd /home/ubuntu/app && docker compose pull backend && docker compose up -d --no-deps backend"
                        """
                        } // withEnv
                    }
                    }
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
            echo "PASSED: ${env.BRANCH_NAME} | ${env.IMAGE_TAG}"
        }
        failure {
            echo "FAILED: ${env.BRANCH_NAME} | ${env.IMAGE_TAG} | ${env.BUILD_URL}"
        }
    }
}
