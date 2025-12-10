# @nadlowebagentur/capacitor-pdf-viewer

Native PDF viewer

## Install

```bash
npm install @nadlowebagentur/capacitor-pdf-viewer
npx cap sync
```

## API

<docgen-index>

* [`open(...)`](#open)
* [`close()`](#close)
* [`getStatus()`](#getstatus)
* [Interfaces](#interfaces)

</docgen-index>

<docgen-api>
<!--Update the source file JSDoc comments and rerun docgen to update the docs below-->

### open(...)

```typescript
open(params: { url: string; title?: string; top?: number; }) => Promise<void>
```

| Param        | Type                                                        |
| ------------ | ----------------------------------------------------------- |
| **`params`** | <code>{ url: string; title?: string; top?: number; }</code> |

--------------------


### close()

```typescript
close() => Promise<void>
```

--------------------


### getStatus()

```typescript
getStatus() => Promise<PDFViewerStatus>
```

**Returns:** <code>Promise&lt;<a href="#pdfviewerstatus">PDFViewerStatus</a>&gt;</code>

--------------------


### Interfaces


#### PDFViewerStatus

| Prop            | Type                 |
| --------------- | -------------------- |
| **`isOpen`**    | <code>boolean</code> |
| **`isAtEnd`**   | <code>boolean</code> |
| **`page`**      | <code>number</code>  |
| **`pageCount`** | <code>number</code>  |

</docgen-api>
