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

        //  1b. Validate Branch Name  all branches ──
        stage('Validate Branch Name') {
            steps {
                script {
                    // PRs: validate the source branch; direct pushes: validate BRANCH_NAME
                    def branchToCheck = env.CHANGE_BRANCH ?: env.BRANCH_NAME
                    sh "chmod +x scripts/validate_branch_name.sh && scripts/validate_branch_name.sh '${branchToCheck}'"
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
                    waitForQualityGate abortPipeline: true
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

                        # /tmp on this agent is too small for Trivy's ~900MB Java DB download
                        # (fails with "no space left on device"). Stage the download/cache on the
                        # workspace disk instead, which has room, and clean up afterwards.
                        export TMPDIR="${env.WORKSPACE}/.trivy-tmp"
                        mkdir -p "\$TMPDIR"

                        # The mirror.gcr.io pull-through cache for the Java DB artifact
                        # intermittently 404s on individual object fetches. Retry a few times
                        # before failing the build, since this is a transient upstream registry
                        # issue rather than anything wrong locally — the cache dir persists
                        # across attempts so a successful partial download isn't redone.
                        attempt=1
                        until trivy image --timeout 30m --exit-code 0 --skip-dirs .git --scanners vuln --format table \\
                            --cache-dir "${env.WORKSPACE}/.trivy-cache" ${env.appName}:${env.IMAGE_TAG} > trivy-image-scan.txt; do
                            if [ "\$attempt" -ge 3 ]; then
                                echo "Trivy scan failed after 3 attempts"
                                cat trivy-image-scan.txt || true
                                rm -rf "\$TMPDIR" "${env.WORKSPACE}/.trivy-cache"
                                exit 1
                            fi
                            echo "Trivy scan attempt \$attempt failed, retrying in 15s..."
                            sleep 15
                            attempt=\$((attempt + 1))
                        done
                        cat trivy-image-scan.txt
                        rm -rf "\$TMPDIR" "${env.WORKSPACE}/.trivy-cache"
                    """
                }
            }
        }

        // ── 9. Push to ECR ──────────────────────────────── develop | staging ──
        stage('Push to ECR') {
            when {
                anyOf {
                    branch 'develop'
                    branch 'staging'
                }
            }
            steps {
                script {
                    withCredentials([string(credentialsId: 'hilfe-v2-backend-deployment-role-arn', variable: 'ROLE_ARN')]) {
                    withAWS(role: "${ROLE_ARN}", roleSessionName: 'jenkins-hilfe-v2-backend-deploy') {
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
        }

        // ── 10a. Deploy to Testing EC2 ─────────────────────────── develop only ──
        stage('Deploy to Testing') {
            when {
                branch 'develop'
            }
            steps {
                script {
                    withCredentials([string(credentialsId: 'hilfe-v2-backend-deployment-role-arn', variable: 'ROLE_ARN')]) {
                    withAWS(role: "${ROLE_ARN}", roleSessionName: 'jenkins-hilfe-v2-backend-deploy') {
                    withCredentials([string(credentialsId: 'testing-backend-ec2-ip', variable: 'EC2_IP')]) {
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

                            scp \${SSH_OPTS} -i "\${SSH_KEY}" nginx-host-backend.conf "ubuntu@\${EC2_IP}:/tmp/nginx-host-backend.conf"
                            ssh \${SSH_OPTS} -i "\${SSH_KEY}" "ubuntu@\${EC2_IP}" \\
                                "sudo cp /tmp/nginx-host-backend.conf /etc/nginx/sites-available/hilfe-backend && \
                                 sudo ln -sf /etc/nginx/sites-available/hilfe-backend /etc/nginx/sites-enabled/hilfe-backend && \
                                 sudo nginx -t && sudo systemctl reload nginx"

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

        // ── 10b. Deploy to Staging EC2 ─────────────────────────── staging only ──
        stage('Deploy to Staging') {
            when {
                branch 'staging'
            }
            steps {
                script {
                    withCredentials([string(credentialsId: 'hilfe-v2-backend-deployment-role-arn', variable: 'ROLE_ARN')]) {
                    withAWS(role: "${ROLE_ARN}", roleSessionName: 'jenkins-hilfe-v2-backend-deploy') {
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

                            # Install nginx if not present (idempotent)
                            ssh \${SSH_OPTS} -i "\${SSH_KEY}" "ubuntu@\${EC2_IP}" \\
                                "command -v nginx &>/dev/null || (sudo apt-get update -q && sudo apt-get install -y -q nginx)"

                            ECR_TOKEN=\$(aws ecr get-login-password --region "${region}")
                            echo "\${ECR_TOKEN}" | ssh \${SSH_OPTS} -i "\${SSH_KEY}" "ubuntu@\${EC2_IP}" \\
                                "docker login --username AWS --password-stdin \${ECR_URL}"

                            ssh \${SSH_OPTS} -i "\${SSH_KEY}" "ubuntu@\${EC2_IP}" \\
                                "mkdir -p /home/ubuntu/app && \
                                 touch /home/ubuntu/app/.env && \
                                 sed -i '/^BACKEND_IMAGE=/d' /home/ubuntu/app/.env && \
                                 echo 'BACKEND_IMAGE=\${ECR_REPO}:${imageTag}' >> /home/ubuntu/app/.env"

                            scp \${SSH_OPTS} -i "\${SSH_KEY}" docker-compose.staging.yml "ubuntu@\${EC2_IP}:/home/ubuntu/app/docker-compose.yml"
                            scp \${SSH_OPTS} -i "\${SSH_KEY}" alloy-config.river "ubuntu@\${EC2_IP}:/home/ubuntu/app/alloy-config.river"

                            scp \${SSH_OPTS} -i "\${SSH_KEY}" nginx-host-backend-staging.conf      "ubuntu@\${EC2_IP}:/tmp/nginx-host-backend.conf"
                            scp \${SSH_OPTS} -i "\${SSH_KEY}" nginx-host-backend-staging-init.conf "ubuntu@\${EC2_IP}:/tmp/nginx-host-backend-init.conf"
                            ssh \${SSH_OPTS} -i "\${SSH_KEY}" "ubuntu@\${EC2_IP}" \\
                                "CERT=/etc/letsencrypt/renewal/hilfe-pro-service-stage.amalitech-dev.net.conf && \
                                 if [ -f \\\$CERT ]; then \
                                   echo 'Certs exist — deploying SSL config'; \
                                   sudo cp /tmp/nginx-host-backend.conf /etc/nginx/sites-available/hilfe-backend; \
                                 else \
                                   echo 'No certs yet — deploying HTTP-only config'; \
                                   sudo cp /tmp/nginx-host-backend-init.conf /etc/nginx/sites-available/hilfe-backend; \
                                 fi && \
                                 sudo ln -sf /etc/nginx/sites-available/hilfe-backend /etc/nginx/sites-enabled/hilfe-backend && \
                                 sudo rm -f /etc/nginx/sites-enabled/default && \
                                 sudo nginx -t && sudo systemctl enable nginx && sudo systemctl restart nginx"

                            ssh \${SSH_OPTS} -i "\${SSH_KEY}" "ubuntu@\${EC2_IP}" \\
                                "cd /home/ubuntu/app && docker compose pull backend node-exporter alloy && docker compose up -d --no-deps backend node-exporter alloy"
                        """
                        } // withEnv
                    }
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
