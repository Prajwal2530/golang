import re; print("\n".join(set(re.findall(r"egov\.[a-zA-Z0-9.-]+host", open("application.jar", "rb").read().decode("latin1")))))
