test {
    jvmArgs '-javaagent:${THUNDRA_AGENT_PATH}'
    environment 'THUNDRA_APIKEY', '${THUNDRA_APIKEY}'
    environment 'THUNDRA_AGENT_TEST_PROJECT_ID', '${THUNDRA_AGENT_TEST_PROJECT_ID}'
}