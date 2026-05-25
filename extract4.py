import re; print(str(set(re.findall(r"\$\{([a-zA-Z0-9.-]*enc[a-zA-Z0-9.-]*host[^}]*)\}", open("application.jar", "rb").read().decode("latin1", errors="ignore")))))
