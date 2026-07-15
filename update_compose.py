import os
from ruamel.yaml import YAML

def update_compose_file(filepath):
    if not os.path.exists(filepath):
        print(f"File {filepath} not found.")
        return

    yaml = YAML()
    yaml.preserve_quotes = True
    yaml.indent(mapping=2, sequence=4, offset=2)
    
    with open(filepath, 'r', encoding='utf-8') as f:
        data = yaml.load(f)

    if 'services' not in data:
        print(f"No services found in {filepath}.")
        return

    infra_services = ['postgres', 'bpa-postgres', 'zookeeper', 'bpa-zookeeper', 'kafka', 'bpa-kafka', 'redis', 'bpa-redis', 'es-cluster', 'elasticsearch', 'minio']

    for service_name, service_data in data['services'].items():
        if not isinstance(service_data, dict):
            continue

        # 1. Logging limits for ALL services
        if 'logging' not in service_data:
            service_data['logging'] = {}
        service_data['logging']['driver'] = 'json-file'
        service_data['logging']['options'] = {
            'max-size': '20m',
            'max-file': '3'
        }

        # 2. Restart policy
        if service_name in infra_services:
            service_data['restart'] = 'always'
        else:
            service_data['restart'] = 'no'

        # 3. Environment variable LOGGING_LEVEL_ROOT for JVM/egov services
        # We can guess it's a java service if it has JAVA_TOOL_OPTIONS or is an egov/bpa service
        is_java_service = False
        image = service_data.get('image', '')
        if 'egovio' in image or 'bpa' in image or 'JAVA_TOOL_OPTIONS' in str(service_data.get('environment', '')):
            is_java_service = True
        
        # Don't modify nodejs or infra services' env vars blindly, focus on egovio/java ones
        if is_java_service and service_name not in infra_services:
            if 'environment' not in service_data:
                service_data['environment'] = {}
            
            env = service_data['environment']
            if isinstance(env, dict):
                env['LOGGING_LEVEL_ROOT'] = 'WARN'
            elif isinstance(env, list):
                # Check if it already exists
                has_logging_level = False
                for e in env:
                    if e.startswith('LOGGING_LEVEL_ROOT='):
                        has_logging_level = True
                        break
                if not has_logging_level:
                    env.append('LOGGING_LEVEL_ROOT=WARN')

    with open(filepath, 'w', encoding='utf-8') as f:
        yaml.dump(data, f)
    
    print(f"Successfully updated {filepath}")

if __name__ == "__main__":
    update_compose_file('docker-compose.yml')
    update_compose_file('docker-compose.bpa.yml')
