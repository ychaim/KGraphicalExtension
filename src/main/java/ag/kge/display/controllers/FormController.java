/*
 * K Graphical Extension
 * Copyright (C) 2014  Adnan A Gazi
 * Contact: adnan.gazi01@gmail.com
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package ag.kge.display.controllers;

import ag.kge.control.ModelCache;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Allows positioning and different classes to be given to entries in a
 * dictionary.
 * Can either display child widgets defined by programmer or by data binding
 */
public class FormController extends AbstractController {

    private final LinkedList<AbstractController> children = new LinkedList<>();
    private final GridBagConstraints gbc = new GridBagConstraints();
    private final LinkedBlockingQueue<String> outQueue;
    private final boolean hasDataBinding;

    public FormController(TreeMap<String, Object> template,
                          LinkedBlockingQueue<String> outQueue){

        this.outQueue = outQueue;
        hasDataBinding = filterData(template);
        setLayout(new GridBagLayout());
        setName(template.get("name").toString());
        if (!hasDataBinding) //it's a standard panel
            addChildrenToPanel(template);
        else { //it needs to display some data
            ModelCache.INSTANCE.addObserver(this.binding = template.
                                get("binding").toString(), this);
            outQueue.add("gUpdate[`" + binding + "; ()]");
        }
    }

    /**
     * Creates a default template required by the controllers
     * for each value in the bound dictionary.
     * Vector-value dictionaries should just have single types.
     * @param variable
     * @return
     */
    private TreeMap<String, Object> createDefaultTemplate(String variable){
        TreeMap<String, Object> template = new TreeMap<>();

        template.put("name", variable);
        template.put("label",variable);
        template.put("binding",this.binding + "." +variable);
        template.put("class", "data");

        return template;
    }

    /**
     * Places widgets as determined by their positioning after selecting their
     * controller
     * @param template
     */
    private void addChildrenToPanel(TreeMap<String,Object> template){

        AbstractController widget;
        String currentB;
        for (Object x: template.values())
            if (x instanceof TreeMap) {
                TreeMap<String,Object> h = (TreeMap<String, Object>) x;

                if (h.containsKey("width"))
                    gbc.gridwidth= (Integer) h.get("width");
                else
                    gbc.gridwidth= 1;

                if (h.containsKey("height"))
                    gbc.gridheight = (Integer) h.get("height");
                else
                    gbc.gridheight= 1;

                gbc.fill = GridBagConstraints.BOTH;
                //fills the given space on the grid

                if (h.containsKey("x"))
                    gbc.gridx = (Integer) h.get("x");
                else
                    gbc.gridx = 0;

                //puts variables in a list if their y values have not been set
                if (h.containsKey("y")) {
                    gbc.gridy = (Integer) h.get("y");
                } else
                    gbc.gridy = 0;

                if (h.get("class").equals("list"))
                    gbc.ipadx=20; //prevents lists from being too thin
                else
                    gbc.ipadx=0;

                children.add(widget = selectController(h));
                //select the controller

                if (!hasDataBinding){
                    if (!(widget instanceof ButtonController)) {
                        ModelCache.INSTANCE.addObserver(currentB =
                                h.get("binding").toString(), widget);
                        outQueue.add("gUpdate[`" + currentB + "; ()]");
                    }
                }
                add(widget, gbc);
            }
    }

    /**
     * Instantiates a controller based on the `class` attribute.
     *
     * @param template
     * @return
     */
    private AbstractController selectController(TreeMap<String,Object> template) {

        switch (template.get("class").toString()){
            case "data":
                return new TextFieldController(template,outQueue);
            case "button":
                return new ButtonController(template,outQueue);
            case "list":
                return new ListController(template,outQueue);
            case "check":
                return new CheckButtonController(template,outQueue);
            case "form": //needs to externally set label
                AbstractController c =  new FormController(template,outQueue);
                c.setBorder(new TitledBorder(template.get("label").toString()));
                return c;
        }
        return null;
    }

    @Override
    public String generateQuery() {
        return null;
    }

    @Override
    public Boolean filterData(Object data) {
        //returns whether the template contains a binding attribute
        TreeMap d = (TreeMap) data;
        if (d.containsKey("binding")) return true;
        else return false;
    }


    @Override
    public void update(Observable o, Object arg) {

        ArrayList updateList = (ArrayList) arg;
        Object head = updateList.get(0);
        TreeMap templateData;

        if (head instanceof TreeMap){
            //whole dictionary given

            //cast argument to tree map
            TreeMap<String, Object> createMap = new TreeMap<>();
            templateData = (TreeMap) head;

            //create default widget templates with keys, put them
            // in new template
            for (Object x: templateData.keySet())
                createMap.put(x.toString(),
                        createDefaultTemplate(x.toString()));

            int maxY = 0;

            for (Object x: createMap.values()){
                ((TreeMap) x).put("y", maxY);
                maxY++;
            }

            addChildrenToPanel(createMap);
            //add children using new template

            //update child widgets with values to populate them
            for (Object x: templateData.keySet())
                for (AbstractController c : children)
                    if (x.toString().equals(c.getName()))
                        c.update(null,
                                Arrays.asList(templateData.get(x)));

        } else {
            //the head is a symbol of the name of the child
            // to be updated
            String childName = head.toString();

            //equivalent of popping off the head of the stack
            List newList = updateList.subList(1, updateList.size());

            //update relevant widgets
            for (AbstractController x: children){
                if (x.getName().equals(childName)){
                    x.update(null,newList);
                }
            }
        }
    }
}
