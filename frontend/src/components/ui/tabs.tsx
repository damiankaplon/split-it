import * as React from "react"
import {Tabs as TabsPrimitive} from "radix-ui"
import {cn} from "@/lib/utils"

function Tabs({className, ...props}: React.ComponentProps<typeof TabsPrimitive.Root>) {
  return <TabsPrimitive.Root data-slot="tabs" className={cn("flex flex-col gap-6", className)} {...props} />
}

function TabsList({className, ...props}: React.ComponentProps<typeof TabsPrimitive.List>) {
  return (
    <TabsPrimitive.List
      data-slot="tabs-list"
      className={cn(
        "inline-flex h-11 w-fit items-center gap-0.5 rounded-full bg-muted p-1 text-muted-foreground shadow-[inset_0_1px_2px_rgb(0_0_0/0.06)]",
        className
      )}
      {...props}
    />
  )
}

function TabsTrigger({className, ...props}: React.ComponentProps<typeof TabsPrimitive.Trigger>) {
  return (
    <TabsPrimitive.Trigger
      data-slot="tabs-trigger"
      className={cn(
        "inline-flex h-full items-center justify-center rounded-full px-5 text-sm font-medium whitespace-nowrap transition-[color,box-shadow] outline-none hover:text-foreground focus-visible:ring-4 focus-visible:ring-ring/20 disabled:pointer-events-none disabled:opacity-50",
        "data-[state=active]:bg-card data-[state=active]:text-foreground data-[state=active]:shadow-card",
        className
      )}
      {...props}
    />
  )
}

function TabsContent({className, ...props}: React.ComponentProps<typeof TabsPrimitive.Content>) {
  return <TabsPrimitive.Content data-slot="tabs-content"
                                className={cn("flex flex-col gap-6 outline-none", className)} {...props} />
}

export {Tabs, TabsList, TabsTrigger, TabsContent}
