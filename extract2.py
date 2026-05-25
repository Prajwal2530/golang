import re; print("\n".join(set(re.findall(r"\$\{([^}]+)\}", open("application.jar", "rb").read().decode("latin1")))))
