//ToDo: use main branch
branchName = 'jadams/unified-templated-test'

library identifier: "zorg-shared-lib@${branchName}",
        retriever: modernSCM([
            $class: 'GitSCMSource',
            remote: "https://github.com/justice-adams-apple/llvm-zorg.git",
            credentialsId: scm.userRemoteConfigs[0].credentialsId
        ])

relay.pipeline([
    "Green-Dragon-Testing/lnt-ctmark-aarch64-O0-g",
    "Green-Dragon-Testing/lnt-ctmark-aarch64-O3-flto",
    "Green-Dragon-Testing/lnt-ctmark-aarch64-Os",
    "Green-Dragon-Testing/lnt-ctmark-aarch64-Oz"
])