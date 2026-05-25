import re; print(str(set(re.findall(r"\$\{([^:]+):http://localhost:1234[^}]*\}", open("application.jar", "rb").read().decode("latin1")))))
