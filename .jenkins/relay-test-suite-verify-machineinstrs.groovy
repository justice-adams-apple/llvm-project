//ToDo: use main branch
branchName = 'jadams/unified-templated-test'

library identifier: "zorg-shared-lib@${branchName}",
        retriever: modernSCM([
            $class: 'GitSCMSource',
            remote: "https://github.com/justice-adams-apple/llvm-zorg.git",
            credentialsId: scm.userRemoteConfigs[0].credentialsId
        ])

jobs = [
    "Green-Dragon-Testing/test-suite-verify-machineinstrs-x86_64-O0-g",
    "Green-Dragon-Testing/test-suite-verify-machineinstrs-x86_64-O3",
    "Green-Dragon-Testing/test-suite-verify-machineinstrs-x86_64h-O3",
    "Green-Dragon-Testing/test-suite-verify-machineinstrs-aarch64-globalisel-O0-g",
    "Green-Dragon-Testing/test-suite-verify-machineinstrs-aarch64-O0-g",
    "Green-Dragon-Testing/test-suite-verify-machineinstrs-aarch64-O3"
]

relay.pipeline jobs
