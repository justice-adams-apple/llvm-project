branchName = 'jadams/bisection-filter'

library identifier: "zorg-shared-lib@${branchName}",
        retriever: modernSCM([
            $class: 'GitSCMSource',
            remote: "https://github.com/llvm/llvm-zorg.git",
            credentialsId: scm.userRemoteConfigs[0].credentialsId
        ])

clangPipeline(
    jobName: env.JOB_NAME,
    zorgBranch: branchName,
    buildConfig: [
        stage: 1,
        build_type: 'cmake',
        cmake_type: 'default',
        assertions: true,
        timeout: 360,
        incremental: false,
        cmake_flags: [
            "-DLLVM_ENABLE_EXPENSIVE_CHECKS=ON",
            "-DLIBCXX_ENABLE_SHARED=OFF",
            "-DLIBCXX_ENABLE_STATIC=OFF",
            "-DLIBCXX_INCLUDE_TESTS=OFF",
            "-DLIBCXX_ENABLE_EXPERIMENTAL_LIBRARY=OFF"
        ]
    ],
    testConfig: [
        test_type: 'testlong',
        timeout: 360,
        junit_patterns: [
            "clang-build/**/testresults.xunit.xml"
        ]
    ]
)
