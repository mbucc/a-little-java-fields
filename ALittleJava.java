// Copyright 2022 Mark Bucciarelli <mkbucc@gmail.com>
// 
// Permission to use, copy, modify, and/or distribute this software for any
// purpose with or without fee is hereby granted, provided that the above
// copyright notice and this permission notice appear in all copies.
// 
// THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
// WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
// MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY
// SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
// WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
// ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR
// IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

public class ALittleJava {

  interface FieldReducerI {
    Object forPrimaryKey(FieldD x);
    Object forColumn(FieldD x);
    Object forEndOfFields();
  }

  abstract class FieldD {
    abstract Object reduce(FieldReducerI ask);
  }

  class PrimaryKey extends FieldD {
    Column[] cols;
    FieldD next;
    PrimaryKey(Column[] _cols, FieldD _next) {
      cols = _cols;
      next = _next; }
    //---------------------------------------------------------
    public Object reduce(FieldReducerI ask) {
      return ask.forPrimaryKey(this); }
    public String toString() {
      return String.format("new %s(%s, %s)",
        getClass().getName(),
        Arrays.toString(cols),
        next); }
  }

  class Column extends FieldD {
    String name;
    String label;
    FieldD next;
    Column(String _name, String _label, FieldD _next) {
      name = _name;
      label = _label;
      next = _next; }
    //---------------------------------------------------------
    public Object reduce(FieldReducerI ask) {
      return ask.forColumn(this); }
    public String toString() {
      return String.format("new %s(%s, %s, %s)", getClass().getName(), name, label, next); }
  }

  class EndOfFields extends FieldD {
    public Object reduce(FieldReducerI ask) {
      return ask.forEndOfFields(); }
    public String toString() {
      return "new " + getClass().getName() + "()"; }
  }

  class SelectSQLV implements FieldReducerI {
    String fields;
    String table;
    String where;
    SelectSQLV(String _fields, String _table, String _where) {
      fields = _fields;
      table = _table;
      where = _where; }
    //---------------------------------------------------------
    public Object forPrimaryKey(FieldD x) {
      where = "";
      for (Column c: ((PrimaryKey) x).cols) {
        if (!where.isEmpty())
          where += " AND ";
        where += ((Column) c).name + " = ?"; }
      return ((PrimaryKey) x).next.reduce(
          new SelectSQLV(fields, table, where)); }
    public Object forColumn(FieldD x) {
      if (!fields.isEmpty())
        fields += ", ";
      fields += ((Column) x).name;
      return ((Column) x).next.reduce(
          new SelectSQLV(fields, table, where)); }
    public Object forEndOfFields() {
      return this; }
    public String toString() {
      return String.format("new \"%s\"(\"%s\", \"%s\", \"%s\")",
        getClass().getName(),
        fields,
        table,
        where); }
    public String toSQL() {
      return String.format("SELECT %s FROM %s WHERE %s", fields, table, where); }
  }


  // https://docs.oracle.com/javase/tutorial/java/javaOO/nested.html
  class Main {
    public static void main(String[] args) {
      var x = new ALittleJava();
      FieldD y =
        x.new PrimaryKey(new Column[] {x.new Column("id", "Customer ID", null)},
          x.new Column("id", "Customer ID",
            x.new Column("name", "Customer Name",
              x.new Column("email", "Customer email",
                x.new EndOfFields()))));
      System.out.println("y = " + y);
      System.out.println(y.reduce(x.new SelectSQLV("", "customer_t", "")));
      SelectSQLV v = (SelectSQLV) y.reduce(
        x.new SelectSQLV(
          "",
          "customer_t",
          ""));
      System.out.println(v.toSQL());
    }
  }
}
