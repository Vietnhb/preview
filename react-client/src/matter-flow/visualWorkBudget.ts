import { parse } from "acorn";
import type { VisualProgram } from "./visualCodeSafety";

type Node = { type: string; start: number; end: number; [key: string]: unknown };
const signatures = { init: "params,width,height", step: "state,dt,params,width,height",
  draw: "state,paint,params,width,height" };
const arrayMethods = new Set(["push", "pop", "shift", "unshift", "slice"]);
const guard = "if(++__visualWork>2000)throw Error('Visual work budget exceeded.');";
const enter = guard + "if(++__visualDepth>128)throw Error('Visual call depth exceeded.');try{";
const leave = "}finally{--__visualDepth;}";
const support = `let __visualWork=0,__visualDepth=0;
const __visualValue=(value,text=false)=>{
  const checked=text?\`\${value}\`:value;
  if(typeof checked==='string'&&checked.length>256000)
    throw Error('Visual string exceeded its data budget.');
  return checked;
};
const __visualIndex=(value)=>{
  if(typeof value!=='number'||!Number.isInteger(value)||value<0||value>=2000)
    throw Error('Visual array index is outside the data budget.');
  return value;
};
const __visualArray=(array,method,...args)=>{
  if(!Array.isArray(array)||array.length>2000
    ||((method==='push'||method==='unshift')&&array.length+args.length>2000))
    throw Error('Visual array exceeded its data budget.');
  return array[method](...args);
};
`;
function isNode(value: unknown): value is Node {
  return !!value && typeof value === "object" && "type" in value && "start" in value && "end" in value;
}

/** Called only after capability validation; bounds work, strings and owned array operations. */
export function instrumentVisualProgram(program: VisualProgram): VisualProgram {
  const output = { ...program };
  for (const phase of Object.keys(signatures) as Array<keyof VisualProgram>) {
    const prefix = `function __visual(${signatures[phase]}){\n`;
    const source = prefix + program[phase] + "\n}";
    const arrows: number[] = [];
    const tree = parse(source, { ecmaVersion: 2022, sourceType: "script",
      onToken: (token) => { if (token.type.label === "=>") arrows.push(token.start); },
    }) as unknown as Node;
    const rewrite = (node: Node): string => {
      if (node.type === "TemplateLiteral") {
        const quasis = node.quasis as Node[];
        const expressions = node.expressions as Node[];
        let result = JSON.stringify((quasis[0].value as { cooked: string }).cooked);
        for (let i = 0; i < expressions.length; i++) {
          // Convert each interpolation before evaluating the next one, as native templates do.
          result = "__visualValue(" + result + "+__visualValue((" + rewrite(expressions[i]) + "),true))";
          const tail = (quasis[i + 1].value as { cooked: string }).cooked;
          if (tail) result = "__visualValue(" + result + "+" + JSON.stringify(tail) + ")";
        }
        return result;
      }
      if (node.type === "CallExpression") {
        const callee = node.callee as Node;
        const property = callee?.property as Node;
        if (callee?.type === "MemberExpression" && !callee.computed
          && arrayMethods.has(String(property?.name))) {
          const args = (node.arguments as Node[]).map(rewrite);
          return "__visualArray(" + rewrite(callee.object as Node) + ",\"" + property.name
            + "\"" + (args.length ? "," + args.join(",") : "") + ")";
        }
      }
      if (node.type === "MemberExpression" && node.computed) {
        return rewrite(node.object as Node) + "[__visualIndex(" + rewrite(node.property as Node) + ")]";
      }
      const children = Object.values(node).flatMap((value) => isNode(value) ? [value]
        : Array.isArray(value) ? value.filter(isNode) : []);
      const unique = [...new Map(children.map((child) => [child.start + ":" + child.end, child])).values()]
        .sort((a, b) => b.start - a.start);
      const assemble = (replacement?: { node: Node; text: string }) => {
        let result = source.slice(node.start, node.end);
        for (const child of unique) {
          const rendered = replacement?.node === child ? replacement.text : rewrite(child);
          result = result.slice(0, child.start - node.start) + rendered
            + result.slice(child.end - node.start);
        }
        return result;
      };
      if (["ForStatement", "WhileStatement", "DoWhileStatement", "ForOfStatement", "ForInStatement"].includes(node.type)) {
        const body = node.body as Node;
        const rendered = rewrite(body);
        return assemble({ node: body, text: body.type === "BlockStatement"
          ? "{" + guard + rendered.slice(1) : "{" + guard + rendered + "}" });
      }
      if (["FunctionDeclaration", "FunctionExpression", "ArrowFunctionExpression"].includes(node.type)
        && node.start !== 0) {
        const body = node.body as Node;
        const rendered = rewrite(body);
        if (node.type === "ArrowFunctionExpression" && body.type !== "BlockStatement") {
          const arrow = arrows.filter((at) => at >= node.start && at < body.start).pop()!;
          return source.slice(node.start, arrow + 2) + "{" + enter + "return (" + rendered + ");" + leave + "}";
        }
        return assemble({ node: body, text: body.type === "BlockStatement"
          ? "{" + enter + rendered.slice(1, -1) + leave + "}"
          : "{" + enter + "return (" + rendered + ");" + leave + "}" });
      }
      const rendered = assemble();
      if (node.type === "BinaryExpression" && node.operator === "+"
        || node.type === "AssignmentExpression" && node.operator === "+=") {
        return "__visualValue(" + rendered + ")";
      }
      return rendered;
    };
    output[phase] = support + rewrite(tree).slice(prefix.length, -2);
  }
  return output;
}
