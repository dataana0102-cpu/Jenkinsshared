def call(Map cfg) {

    pipeline {
        agent any

        environment {
            IMAGE = "${cfg.dockerUser}/${cfg.image}:${cfg.tag}"
        }

        stages {

            /* ---------------- Git checkout ---------------- */

            stage('Checkout') {
                steps {
                    checkout([
                        $class: 'GitSCM',
                        branches: [[name: cfg.branch]],
                        userRemoteConfigs: [[
                            url: cfg.repoUrl,
                            credentialsId: cfg.gitCreds
                        ]]
                    ])
                }
            }

            /* ---------------- Git commit (generated file only) ---------------- */

            stage('Update build info & commit') {
                steps {
                    sh """
                      echo ${cfg.tag} > build-version.txt
                      git config user.email "ci@company.com"
                      git config user.name  "jenkins-ci"

                      git add build-version.txt
                      git commit -m "ci: update build version ${cfg.tag}" || echo "Nothing to commit"
                    """
                }
            }

            /* ---------------- Git push ---------------- */

            stage('Push commit to Git') {
                steps {
                    withCredentials([usernamePassword(
                        credentialsId: cfg.gitCreds,
                        usernameVariable: 'GIT_USER',
                        passwordVariable: 'GIT_PASS'
                    )]) {

                        sh """
                          git push https://$GIT_USER:$GIT_PASS@${cfg.repoHost}/${cfg.repoPath}.git HEAD:${cfg.branchName}
                        """
                    }
                }
            }

            /* ---------------- Docker build ---------------- */

            stage('Docker Build') {
                steps {
                    sh "docker build -t ${IMAGE} ."
                }
            }

            /* ---------------- Docker push (Docker Hub) ---------------- */

            stage('Docker Push') {
                steps {
                    withCredentials([usernamePassword(
                        credentialsId: cfg.dockerCreds,
                        usernameVariable: 'DOCKER_USER',
                        passwordVariable: 'DOCKER_PASS'
                    )]) {

                        sh """
                          echo $DOCKER_PASS | docker login -u $DOCKER_USER --password-stdin
                          docker push ${IMAGE}
                        """
                    }
                }
            }
        }
    }
}
