//ToDo: use main branch
branchName = 'jadams/unified-templated-test'

library identifier: "zorg-shared-lib@${branchName}",
        retriever: modernSCM([
            $class: 'GitSCMSource',
            remote: "https://github.com/justice-adams-apple/llvm-zorg.git",
            credentialsId: scm.userRemoteConfigs[0].credentialsId
        ])

common.testsuite_pipeline(label: 'macos-x86_64-lnt') { // ToDo: Change label
    sh """
CMAKE_FLAGS+=" -C ../config/tasks/cmake/caches/target-x86_64h-macos.cmake"
CMAKE_FLAGS+=" -C ../config/tasks/cmake/caches/opt-O3.cmake"
config/tasks/task jenkinsrun config/tasks/test-suite-verify-machineinstrs.sh -a compiler="${params.ARTIFACT}" -D CMAKE_FLAGS="\${CMAKE_FLAGS}"
    """
}
