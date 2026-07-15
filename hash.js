const bcrypt = require('bcryptjs');
console.log(bcrypt.hashSync('eGov@123', 10).replace('$2b$', '$2a$'));
