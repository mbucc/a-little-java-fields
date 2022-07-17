.PHONY: run
run: ALittleJava.class
	java -cp bld 'ALittleJava$$Main'

ALittleJava.class: ALittleJava.java
	mkdir -p bld
	javac -d bld $?

.PHONY: clean
clean:
	rm -rf bld
