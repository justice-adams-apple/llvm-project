//ToDo: use main branch
branchName = 'jadams/unified-templated-test'

library identifier: "zorg-shared-lib@${branchName}",
        retriever: modernSCM([
            $class: 'GitSCMSource',
            remote: "https://github.com/justice-adams-apple/llvm-zorg.git",
            credentialsId: scm.userRemoteConfigs[0].credentialsId
        ])

clangPipeline(
    jobName: env.JOB_NAME,
    buildConfig: [
        stage: 1,
        build_type: 'cmake',
        cmake_type: 'RelWithDebInfo',
        assertions: true,
        projects: 'clang;clang-tools-extra',
        runtimes: 'compiler-rt',
        timeout: 120,
        incremental: false
    ],
    testConfig: [
        test_type: 'testlong',
        timeout: 120,
        junit_patterns: [
            "clang-build/**/testresults.xunit.xml"
        ]
    ]
//     triggeredJobs: [
//         'clang-stage2-cmake-RgSan_relay',
//         'clang-stage2-Rthinlto_relay',
//         'relay-lnt-ctmark',
//         'relay-test-suite-verify-machineinstrs'
//     ]
)