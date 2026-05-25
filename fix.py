data = open('docker-compose.bpa.yml', encoding='utf-8').read()
data = data.replace('\
', '\n')
data = data.replace('EGOV_WORKFLOW_HOST: \"http://egov-workflow-v2:8080\"`n        WORKFLOW_CONTEXT_PATH: \"http://egov-workflow-v2:8080\"`n        WORKFLOW_WORKDIR_PATH: \"http://egov-workflow-v2:8080\"', 'EGOV_WORKFLOW_HOST: \"http://egov-workflow-v2:8080\"\n        WORKFLOW_CONTEXT_PATH: \"http://egov-workflow-v2:8080\"\n        WORKFLOW_WORKDIR_PATH: \"http://egov-workflow-v2:8080\"')
open('docker-compose.bpa.yml', 'w', encoding='utf-8').write(data)
