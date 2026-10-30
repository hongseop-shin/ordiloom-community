#!/usr/bin/env python3
from pathlib import Path
import shutil,subprocess,re,json,sqlite3
ROOT=Path(__file__).resolve().parents[1];out=ROOT/'build/tests';out.mkdir(parents=True,exist_ok=True)
java=shutil.which('java');javac=[shutil.which('javac')] if shutil.which('javac') else [java,'-m','jdk.compiler/com.sun.tools.javac.Main']
base=ROOT/'app/src/main/java/com/hongseop/ordiloom/community'
subprocess.run(javac+['-encoding','UTF-8','-d',str(out),str(base/'Money.java'),str(base/'Rules.java'),str(ROOT/'tests/DomainTest.java')],check=True)
subprocess.run([java,'-cp',str(out),'DomainTest'],check=True)
source=(base/'Store.java').read_text()
chunk=re.search(r'static final String\[\]\s+CREATE_SQL\s*=\s*\{(.*?)\n\s*\};',source,re.S).group(1)
literal=r'"(?:\\.|[^"\\])*"'
expressions=re.findall(literal+r'(?:\s*\+\s*'+literal+r')*',chunk)
sql=[''.join(json.loads(part) for part in re.findall(literal,expression)) for expression in expressions]
db=sqlite3.connect(':memory:');db.execute('PRAGMA foreign_keys=ON')
for statement in sql:db.execute(statement)
db.execute("INSERT INTO customers(name) VALUES('Customer')")
db.execute("INSERT INTO products(sku,name,sizes,ratios,pack_size,price,stock) VALUES('SKU1','Item','S,M,L','1,2,1',4,250,20)")
db.execute("INSERT INTO orders(customer_id,created,updated) VALUES(1,1,1)")
db.execute("INSERT INTO order_items(order_id,product_id,sku,name,sizes,ratios,pack_size,packs,price) VALUES(1,1,'SKU1','Item','S,M,L','1,2,1',4,3,250)")
db.commit()
bad=["UPDATE products SET stock=-1", "INSERT INTO products(sku,name,sizes,ratios,pack_size,price,stock) VALUES('sku1','Duplicate','S','1',1,100,1)", "UPDATE orders SET status='UNKNOWN'", "UPDATE order_items SET discount=251", "UPDATE order_items SET product_id=999", "DELETE FROM products WHERE id=1"]
for s in bad:
    try:db.execute(s)
    except sqlite3.IntegrityError:db.rollback()
    else:raise AssertionError('Invalid database state accepted: '+s)
db.execute('BEGIN')
db.execute('UPDATE products SET stock=8 WHERE id=1')
db.execute("UPDATE orders SET status='COMPLETE',total=3000 WHERE id=1")
db.rollback()
assert db.execute('SELECT stock FROM products').fetchone()[0]==20
assert db.execute('SELECT status FROM orders').fetchone()[0]=='DRAFT'
print('PASS: schema constraints and multi-table rollback. Runtime repository tests require Android.')
