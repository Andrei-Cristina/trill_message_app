db = connect("mongodb://andrei:andrei@localhost:27017/admin");

db = db.getSiblingDB("trill_message_db");

db.dropUser("andrei");

db.createUser({
    user: "andrei",
    pwd: "andrei",
    roles: [
        { role: "readWrite", db: "trill_message_db" },
        { role: "dbAdmin", db: "trill_message_db" }
    ]
});

print("Users in trill_message_db:", db.getUsers());