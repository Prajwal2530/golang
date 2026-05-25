import re; print(str(set(re.findall(r"\$\{([^}]+host[^}]*)\}", open("application.jar", "rb").read().decode("latin1", errors="ignore")))))
