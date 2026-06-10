branchName = 'jadams/bisection-filter'

library identifier: "zorg-shared-lib@${branchName}",
        retriever: modernSCM([
            $class: 'GitSCMSource',
            remote: "https://github.com/llvm/llvm-zorg.git"
        ])

pipeline {
    options {
        skipDefaultCheckout()
    }

    agent {
        node {
            label 'macos-trigger'
        }
    }

    parameters {
        string(name: 'GOOD_COMMIT', description: 'Known good commit SHA', defaultValue: '')
        string(name: 'BAD_COMMIT', description: 'Known bad commit SHA', defaultValue: '')
        choice(
            name: 'TEST_JOB_NAME',
            choices: [
              'clang-stage1-RA',
              'clang-stage1-RA-cmake-incremental',
              'clang-stage1-RA-expensive',
              'clang-stage1-RA-as',
              'clang-san-iossim',
              'clang-stage2-cmake-RgSan',
              'clang-stage2-cmake-RgTSan',
              'clang-stage2-Rthinlto',
            ],
            description: 'Job to execute for testing each commit'
        )
        choice(
            name: 'REPOSITORY',
            choices: ['llvm-project'],
            description: 'Repository to bisect'
        )
        booleanParam(name: 'RUN_TESTS', defaultValue: true, description: 'Run tests as part of bisection. Set to False if bisecting a build failure.')
        string(name: 'LIT_TEST_FILTER', defaultValue: '', description: 'LIT test filter for bisection. For standard jobs: workspace-relative path passed directly to llvm-lit (e.g. llvm-project/clang/test/CodeGen/foo.c). For custom script jobs (e.g. clang-san-iossim): regex pattern exported as LIT_FILTER env var so lit filters within the custom script (e.g. deep_sleep_test). Leave empty to use the full job result.')
        string(name: 'TEST_REPEAT_COUNT', defaultValue: '3', description: 'Number of times to run the LIT test per commit. All runs must pass for the commit to be marked GOOD — a single failure marks it BAD.')
        string(name: 'SESSION_ID', description: 'Session ID to continue (optional)', defaultValue: '')
        booleanParam(name: 'DRY_RUN', defaultValue: false, description: 'Dry run mode')
        string(name: 'REQUESTOR', description: 'Email address of the requestor', defaultValue: '')
    }

    stages {
        stage('Validate Parameters') {
            steps {
                script {
                    if (!params.GOOD_COMMIT || !params.BAD_COMMIT) {
                        error("Both GOOD_COMMIT and BAD_COMMIT parameters are required")
                    }
                    if (!params.TEST_JOB_NAME) {
                        error("TEST_JOB_NAME parameter is required")
                    }
                }
            }
        }

        stage('Setup Repository') {
            steps {
                script {
                    echo "📁 Setting up repository: ${params.REPOSITORY}..."

                    dir(params.REPOSITORY) {
                        // Clone or checkout the repository
                        checkout([$class: 'GitSCM', branches: [
                            [name: params.GOOD_COMMIT]
                        ], extensions: [
                            [$class: 'CloneOption',
                            timeout: 30]
                        ], userRemoteConfigs: [
                            [url: 'https://github.com/llvm/llvm-project.git']
                        ]])

                        // Verify commits exist
                        sh "git cat-file -e ${params.GOOD_COMMIT}"
                        sh "git cat-file -e ${params.BAD_COMMIT}"

                        // Verify commit order. GOOD_COMMIT must be an ancestor of BAD_COMMIT
                        def correctOrder = sh(
                            script: "git merge-base --is-ancestor ${params.GOOD_COMMIT} ${params.BAD_COMMIT}",
                            returnStatus: true
                        ) == 0
                        if (!correctOrder) {
                            error("GOOD_COMMIT ${params.GOOD_COMMIT} is not an ancestor of BAD_COMMIT ${params.BAD_COMMIT}. Are the commit parameters incorrect?")
                        }

                        if (params.LIT_TEST_FILTER) {
                            def repoPrefix = "${params.REPOSITORY}/"
                            if (params.LIT_TEST_FILTER.startsWith(repoPrefix)) {
                                def gitPath = params.LIT_TEST_FILTER.substring(repoPrefix.length())
                                def existsAtBad = sh(
                                    script: "git cat-file -e ${params.BAD_COMMIT}:${gitPath}",
                                    returnStatus: true
                                ) == 0
                                if (!existsAtBad) {
                                    error("LIT_TEST_FILTER '${params.LIT_TEST_FILTER}' does not exist at BAD_COMMIT ${params.BAD_COMMIT} — verify the test path is correct")
                                }
                            }
                        }

                        echo "Repository setup complete"
                    }
                }
            }
        }

        stage('Initialize Bisection') {
            steps {
                script {
                    bisectionManager.initializeBisection(
                        params.GOOD_COMMIT,
                        params.BAD_COMMIT,
                        params.TEST_JOB_NAME,
                        params.REPOSITORY,
                        params.SESSION_ID ?: null,
                        params.LIT_TEST_FILTER,
                        params.TEST_REPEAT_COUNT.toInteger()
                    )
                }
            }
        }

        stage('Execute Bisection') {
            steps {
                script {
                    def stepNumber = 1
                    def maxSteps = 50

                    while (stepNumber <= maxSteps) {
                        // Log step and get info
                        def stepInfo = bisectionManager.logStepStart(stepNumber, params.REPOSITORY)

                        if (stepInfo.type == 'complete') {
                            echo "Bisection complete! Failing commit: ${stepInfo.failing_commit}"
                            break
                        }

                        // Show restart instructions
                        bisectionManager.showRestartInstructions(
                            stepNumber,
                            params.TEST_JOB_NAME,
                            params.REPOSITORY,
                            params.LIT_TEST_FILTER,
                            params.TEST_REPEAT_COUNT.toInteger()
                        )

                        if (params.DRY_RUN) {
                            def simulatedResult = (stepNumber % 2 == 0) ? "SUCCESS" : "FAILURE"
                            echo "Simulated result: ${simulatedResult}"
                            bisectionManager.recordTestResult(stepInfo.commit, simulatedResult, params.REPOSITORY)
                        } else {
                            // Execute real job
                            def startTime = System.currentTimeMillis()

                            def jobResult = build(
                                job: "Green-Dragon-Testing/bisect/${params.TEST_JOB_NAME}",
                                parameters: [
                                    string(name: 'GIT_SHA', value: stepInfo.commit),
                                    string(name: 'BISECT_GOOD', value: stepInfo.bisection_range.current_good),
                                    string(name: 'BISECT_BAD', value: stepInfo.bisection_range.current_bad),
                                    booleanParam(name: 'IS_BISECT_JOB', value: true),
                                    booleanParam(name: 'SKIP_TESTS', value: !params.RUN_TESTS),
                                    booleanParam(name: 'SKIP_TRIGGER', value: true),
                                    string(name: 'LIT_TEST_FILTER', value: params.LIT_TEST_FILTER),
                                    string(name: 'TEST_REPEAT_COUNT', value: params.TEST_REPEAT_COUNT)
                                ],
                                propagate: false,
                                wait: true
                            )

                            def duration = ((System.currentTimeMillis() - startTime) / 1000.0) as double

                            // Log the job execution
                            bisectionManager.logJobExecution(
                                params.TEST_JOB_NAME,
                                jobResult.result,
                                duration,
                                jobResult.absoluteUrl,
                                jobResult.number.toString(),
                                params.REPOSITORY
                            )

                            // Record the test result
                            bisectionManager.recordTestResult(stepInfo.commit, jobResult.result, params.REPOSITORY)
                        }

                        stepNumber++
                        sleep(2)
                    }

                    if (stepNumber > maxSteps) {
                        error("Bisection exceeded maximum steps")
                    }
                }
            }
        }

        stage('Final Report') {
            steps {
                script {
                    bisectionManager.generateFinalReport(params.REPOSITORY)
                }
            }
        }
    }

    post {
        always {
            script {
                bisectionManager.displaySummary(params.REPOSITORY)
                archiveArtifacts artifacts: "bisection_state.json,bisection.log,restart_instructions.log,bisection_final_report.txt",
                                allowEmptyArchive: true
            }
        }
        cleanup {
            // Clean up but preserve artifacts
            sh 'rm -f bisection_manager.py'
        }
    }
}
